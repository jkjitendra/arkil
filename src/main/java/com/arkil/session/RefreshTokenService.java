package com.arkil.session;

import com.arkil.audit.ActorType;
import com.arkil.audit.AuditEventType;
import com.arkil.audit.AuditService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing refresh tokens with rotation and reuse detection.
 *
 * Security Features:
 * - Token hashing (never store plaintext)
 * - Token families for reuse detection
 * - Automatic rotation on each refresh
 * - Bulk revocation on reuse detection
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository tokenRepository;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${arkil.token.refresh.expiry-days:30}")
    private int refreshTokenExpiryDays;

    @Value("${arkil.token.refresh.max-sessions:10}")
    private int maxSessionsPerUser;

    private static final int TOKEN_BYTES = 32;

    /**
     * Issue a new refresh token for a user (creates new family).
     */
    @Transactional
    public TokenPair issueRefreshToken(UUID userId, String clientId, String userAgent, String ipAddress) {
        // Check and limit active sessions
        long activeSessions = tokenRepository.countActiveByUserId(userId, Instant.now());
        if (activeSessions >= maxSessionsPerUser) {
            log.warn("User {} has {} active sessions, at limit", userId, activeSessions);
            // Could revoke oldest session here, or reject new login
        }

        UUID familyId = UUID.randomUUID();
        return createToken(userId, clientId, familyId, userAgent, ipAddress);
    }

    /**
     * Rotate a refresh token (use old token, issue new one).
     * Returns empty if token is invalid or reuse is detected.
     */
    @Transactional
    public Optional<TokenPair> rotateToken(String oldTokenPlaintext) {
        String tokenHash = hashToken(oldTokenPlaintext);
        Optional<RefreshToken> tokenOpt = tokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            log.debug("Refresh token not found");
            return Optional.empty();
        }

        RefreshToken token = tokenOpt.get();

        // Check if token is valid
        if (!token.isActive()) {
            if (token.getRevocationReason() == RefreshToken.RevocationReason.ROTATION) {
                // REUSE DETECTED! Someone is using an already-rotated token
                log.warn("SECURITY: Refresh token reuse detected for user {}, family {}",
                        token.getUserId(), token.getFamilyId());

                // Revoke entire family
                int revoked = tokenRepository.revokeByFamilyId(
                        token.getFamilyId(),
                        Instant.now(),
                        RefreshToken.RevocationReason.REUSE_DETECTED);

                log.warn("Revoked {} tokens in family {} due to reuse", revoked, token.getFamilyId());

                // Log security event for audit
                auditService.logFailure(AuditEventType.AUTH_TOKEN_REVOKED,
                        token.getUserId().toString(), ActorType.USER,
                        token.getFamilyId().toString(),
                        "Refresh token reuse detected — revoked " + revoked + " tokens in family",
                        null);
            }
            return Optional.empty();
        }

        // Mark old token as used/rotated
        token.markUsed();
        token.revoke(RefreshToken.RevocationReason.ROTATION);
        tokenRepository.save(token);

        // Issue new token in same family
        return Optional.of(createToken(
                token.getUserId(),
                token.getClientId(),
                token.getFamilyId(),
                token.getUserAgent(),
                token.getIpAddress()));
    }

    /**
     * Revoke a specific token (logout).
     */
    @Transactional
    public boolean revokeToken(String tokenPlaintext) {
        String tokenHash = hashToken(tokenPlaintext);
        Optional<RefreshToken> tokenOpt = tokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            return false;
        }

        RefreshToken token = tokenOpt.get();
        token.revoke(RefreshToken.RevocationReason.LOGOUT);
        tokenRepository.save(token);

        log.info("Token revoked for user {}", token.getUserId());
        return true;
    }

    /**
     * Revoke all tokens for a user (password change, security event).
     */
    @Transactional
    public int revokeAllForUser(UUID userId, RefreshToken.RevocationReason reason) {
        int revoked = tokenRepository.revokeByUserId(userId, Instant.now(), reason);
        log.info("Revoked {} tokens for user {} due to {}", revoked, userId, reason);
        return revoked;
    }

    /**
     * Get active sessions for a user.
     */
    public List<RefreshToken> getActiveSessions(UUID userId) {
        return tokenRepository.findActiveByUserId(userId, Instant.now());
    }

    /**
     * Validate a token without consuming it.
     */
    public Optional<RefreshToken> validateToken(String tokenPlaintext) {
        String tokenHash = hashToken(tokenPlaintext);
        return tokenRepository.findByTokenHash(tokenHash)
                .filter(RefreshToken::isActive);
    }

    /**
     * Cleanup expired tokens (scheduled task).
     */
    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = tokenRepository.deleteExpiredTokens(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired refresh tokens", deleted);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Private Methods
    // ─────────────────────────────────────────────────────────────────

    private TokenPair createToken(UUID userId, String clientId, UUID familyId,
                                   String userAgent, String ipAddress) {
        // Generate random token
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String tokenPlaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = hashToken(tokenPlaintext);

        // Create token entity
        RefreshToken token = RefreshToken.builder()
                .tokenHash(tokenHash)
                .familyId(familyId)
                .userId(userId)
                .clientId(clientId)
                .expiresAt(Instant.now().plus(refreshTokenExpiryDays, ChronoUnit.DAYS))
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .build();

        tokenRepository.save(token);

        log.debug("Issued refresh token for user {}, family {}", userId, familyId);

        return new TokenPair(tokenPlaintext, token);
    }

    private String hashToken(String tokenPlaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(tokenPlaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Token pair containing plaintext (for client) and entity (for internal use).
     */
    public record TokenPair(String plaintext, RefreshToken entity) {}
}
