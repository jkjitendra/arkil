package com.arkil.email;

import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing email tokens (verification, reset, magic link).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTokenService {

    private final EmailTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int TOKEN_BYTES = 32;

    /**
     * Send email verification to a user.
     */
    @Transactional
    public void sendVerificationEmail(UUID userId) {
        ArkilUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Invalidate any existing verification tokens
        tokenRepository.invalidateTokensByUserAndType(userId, EmailToken.TokenType.EMAIL_VERIFICATION, Instant.now());

        // Create new token (24 hours validity)
        String token = generateSecureToken();
        EmailToken emailToken = EmailToken.builder()
                .token(token)
                .userId(userId)
                .email(user.getEmail())
                .type(EmailToken.TokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        tokenRepository.save(emailToken);

        // Send email
        emailService.sendVerificationEmail(user.getEmail(), token, user.getUsername());
        log.info("Sent verification email to user: {}", user.getUsername());
    }

    /**
     * Send password reset email.
     */
    @Transactional
    public void sendPasswordResetEmail(String email) {
        Optional<ArkilUser> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // Don't reveal if user exists - just log and return
            log.debug("Password reset requested for non-existent email: {}", email);
            return;
        }

        ArkilUser user = userOpt.get();

        // Invalidate any existing reset tokens
        tokenRepository.invalidateTokensByUserAndType(user.getId(), EmailToken.TokenType.PASSWORD_RESET, Instant.now());

        // Create new token (30 minutes validity)
        String token = generateSecureToken();
        EmailToken emailToken = EmailToken.builder()
                .token(token)
                .userId(user.getId())
                .email(email)
                .type(EmailToken.TokenType.PASSWORD_RESET)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();

        tokenRepository.save(emailToken);

        // Send email
        emailService.sendPasswordResetEmail(email, token, user.getUsername());
        log.info("Sent password reset email to user: {}", user.getUsername());
    }

    /**
     * Send magic link for passwordless login.
     */
    @Transactional
    public void sendMagicLink(String email) {
        Optional<ArkilUser> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.debug("Magic link requested for non-existent email: {}", email);
            return;
        }

        ArkilUser user = userOpt.get();

        // Invalidate any existing magic link tokens
        tokenRepository.invalidateTokensByUserAndType(user.getId(), EmailToken.TokenType.MAGIC_LINK, Instant.now());

        // Create new token (15 minutes validity)
        String token = generateSecureToken();
        EmailToken emailToken = EmailToken.builder()
                .token(token)
                .userId(user.getId())
                .email(email)
                .type(EmailToken.TokenType.MAGIC_LINK)
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();

        tokenRepository.save(emailToken);

        // Send email
        emailService.sendMagicLinkEmail(email, token, user.getUsername());
        log.info("Sent magic link to user: {}", user.getUsername());
    }

    /**
     * Verify an email token.
     */
    @Transactional
    public Optional<EmailToken> verifyToken(String token, EmailToken.TokenType expectedType) {
        Optional<EmailToken> tokenOpt = tokenRepository.findByTokenAndType(token, expectedType);
        if (tokenOpt.isEmpty()) {
            log.debug("Token not found: {}", token);
            return Optional.empty();
        }

        EmailToken emailToken = tokenOpt.get();
        if (!emailToken.isValid()) {
            log.debug("Token invalid (expired or used): {}", token);
            return Optional.empty();
        }

        // Mark token as used
        emailToken.setUsedAt(Instant.now());
        tokenRepository.save(emailToken);

        return Optional.of(emailToken);
    }

    /**
     * Verify email and mark user as verified.
     */
    @Transactional
    public boolean verifyEmail(String token) {
        Optional<EmailToken> tokenOpt = verifyToken(token, EmailToken.TokenType.EMAIL_VERIFICATION);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        EmailToken emailToken = tokenOpt.get();
        ArkilUser user = userRepository.findById(emailToken.getUserId()).orElse(null);
        if (user == null) {
            return false;
        }

        user.setEmailVerified(true);
        userRepository.save(user);
        log.info("Email verified for user: {}", user.getUsername());
        return true;
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
