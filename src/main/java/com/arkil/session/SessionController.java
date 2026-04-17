package com.arkil.session;

import com.arkil.audit.ActorType;
import com.arkil.audit.AuditEventType;
import com.arkil.audit.AuditService;
import com.arkil.audit.ProjectWebhookEventService;
import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.credential.totp.TotpService;
import com.arkil.email.EmailToken;
import com.arkil.email.EmailTokenService;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Session API controller.
 * Part of the Session/UX API surface (Surface B).
 *
 * Handles session creation (login), refresh, and logout.
 * Access tokens are returned in response body.
 * Refresh tokens are set as HttpOnly cookies.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Session management (login, refresh, logout)")
public class SessionController {

    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailTokenService emailTokenService;
    private final TotpService totpService;
    private final AuditService auditService;
    private final ProjectWebhookEventService projectWebhookEventService;
    private final JwtEncoder jwtEncoder;

    @Value("${arkil.session.cookie.same-site:None}")
    private String cookieSameSite;

    @Value("${arkil.session.cookie.secure:true}")
    private String cookieSecure;

    @Value("${arkil.token.refresh.expiry-days:30}")
    private int refreshTokenExpiryDays;

    @Value("${arkil.token.access.expiry-minutes:15}")
    private int accessTokenExpiryMinutes;

    private static final String REFRESH_TOKEN_COOKIE = "arkil_refresh_token";

    public SessionController(RefreshTokenService refreshTokenService,
                             UserRepository userRepository,
                             PasswordCredentialRepository passwordCredentialRepository,
                             PasswordEncoder passwordEncoder,
                             EmailTokenService emailTokenService,
                             TotpService totpService,
                             AuditService auditService,
                             ProjectWebhookEventService projectWebhookEventService,
                             JWKSource<SecurityContext> jwkSource) {
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.passwordCredentialRepository = passwordCredentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailTokenService = emailTokenService;
        this.totpService = totpService;
        this.auditService = auditService;
        this.projectWebhookEventService = projectWebhookEventService;
        this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
    }

    // ─────────────────────────────────────────────────────────────────
    // Session Creation (Login)
    // ─────────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create session (login with password or magic link token)")
    public ResponseEntity<Map<String, Object>> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        log.info("Session creation request for identifier: {}", request.getIdentifier());

        // 1. Look up user by identifier (email or username)
        ArkilUser user = resolveUser(request.getIdentifier());
        if (user == null) {
            auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, request.getIdentifier(),
                    ActorType.USER, null, "User not found", httpRequest);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "invalid_credentials",
                    "message", "Invalid credentials"
            ));
        }

        if (!user.getEnabled()) {
            auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, user.getId().toString(),
                    ActorType.USER, null, "Account disabled", httpRequest);
            return ResponseEntity.status(403).body(Map.of(
                    "error", "account_disabled",
                    "message", "Account is disabled"
            ));
        }

        // 2. Validate credential
        if (request.getMagicLinkToken() != null) {
            // Magic link authentication
            Optional<EmailToken> tokenOpt = emailTokenService.verifyToken(
                    request.getMagicLinkToken(), EmailToken.TokenType.MAGIC_LINK);
            if (tokenOpt.isEmpty() || !tokenOpt.get().getUserId().equals(user.getId())) {
                auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, user.getId().toString(),
                        ActorType.USER, null, "Invalid magic link token", httpRequest);
                return ResponseEntity.status(401).body(Map.of(
                        "error", "invalid_token",
                        "message", "Invalid or expired magic link"
                ));
            }
        } else if (request.getPassword() != null) {
            // Password authentication
            Optional<PasswordCredential> credOpt = passwordCredentialRepository.findByUser_Id(user.getId());
            if (credOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), credOpt.get().getPasswordHash())) {
                auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, user.getId().toString(),
                        ActorType.USER, null, "Invalid password", httpRequest);
                return ResponseEntity.status(401).body(Map.of(
                        "error", "invalid_credentials",
                        "message", "Invalid credentials"
                ));
            }
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_credential",
                    "message", "Password or magic link token is required"
            ));
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, user.getId().toString(),
                    ActorType.USER, null, "Email not verified", httpRequest);
            return ResponseEntity.status(403).body(Map.of(
                    "error", "email_not_verified",
                    "message", "Verify your email address before signing in.",
                    "email", user.getEmail(),
                    "canResendVerification", true,
                    "resendVerificationPath", "/api/v1/auth/resend-verification"
            ));
        }

        // 3. Check MFA requirements
        if (totpService.isEnabled(user.getId())) {
            if (request.getTotpCode() == null || request.getTotpCode().isBlank()) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "mfa_required",
                        "message", "TOTP code is required",
                        "mfaType", "totp"
                ));
            }
            if (!totpService.verify(user.getId(), request.getTotpCode())) {
                auditService.logFailure(AuditEventType.MFA_FAILED, user.getId().toString(),
                        ActorType.USER, "totp", "Invalid TOTP code", httpRequest);
                return ResponseEntity.status(401).body(Map.of(
                        "error", "invalid_totp",
                        "message", "Invalid TOTP code"
                ));
            }
        }

        // 4. Generate tokens
        String clientId = request.getClientId() != null ? request.getClientId() : "default";
        RefreshTokenService.TokenPair tokenPair = refreshTokenService.issueRefreshToken(
                user.getId(), clientId,
                httpRequest.getHeader("User-Agent"),
                extractClientIp(httpRequest));

        // Set refresh token as HttpOnly cookie
        int maxAgeSeconds = refreshTokenExpiryDays * 24 * 60 * 60;
        setRefreshTokenCookie(response, tokenPair.plaintext(), maxAgeSeconds);

        // Generate JWT access token
        String accessToken = mintAccessToken(user, clientId);

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditService.logSuccess(AuditEventType.AUTH_LOGIN_SUCCESS, user.getId().toString(),
                ActorType.USER, clientId, httpRequest);
        projectWebhookEventService.sessionCreated(
                user,
                ActorType.USER,
                user.getId().toString(),
                httpRequest,
                request.getClientId(),
                user.getTenant() != null ? user.getTenant().getId() : null,
                request.getMagicLinkToken() != null ? "magic_link" : "password"
        );

        log.info("Session created for user: {}", user.getEmail());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "accessToken", accessToken,
                "tokenType", "Bearer",
                "expiresIn", accessTokenExpiryMinutes * 60,
                "user", Map.of(
                        "id", user.getId().toString(),
                        "email", user.getEmail(),
                        "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
                )
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Token Refresh
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh cookie")
    public ResponseEntity<Map<String, Object>> refreshSession(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Get refresh token from HttpOnly cookie
        String refreshToken = getRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "no_refresh_token",
                    "message", "Refresh token cookie not found"
            ));
        }

        log.debug("Refresh token request received");

        // Rotate token using RefreshTokenService
        Optional<RefreshTokenService.TokenPair> rotatedOpt = refreshTokenService.rotateToken(refreshToken);

        if (rotatedOpt.isEmpty()) {
            // Token invalid or reuse detected
            clearRefreshTokenCookie(response);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "invalid_token",
                    "message", "Invalid or expired refresh token. Please log in again."
            ));
        }

        RefreshTokenService.TokenPair newTokenPair = rotatedOpt.get();

        // Set new refresh token cookie
        int maxAgeSeconds = refreshTokenExpiryDays * 24 * 60 * 60;
        setRefreshTokenCookie(response, newTokenPair.plaintext(), maxAgeSeconds);

        // Look up user to generate new access token
        ArkilUser user = userRepository.findById(newTokenPair.entity().getUserId()).orElse(null);
        if (user == null || !user.getEnabled()) {
            clearRefreshTokenCookie(response);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "user_not_found",
                    "message", "User account not found or disabled"
            ));
        }

        String accessToken = mintAccessToken(user, newTokenPair.entity().getClientId());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "accessToken", accessToken,
                "tokenType", "Bearer",
                "expiresIn", accessTokenExpiryMinutes * 60
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────

    @DeleteMapping("/current")
    @Operation(summary = "Logout (delete current session)")
    public ResponseEntity<Map<String, String>> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        // Revoke refresh token in database
        String refreshToken = getRefreshTokenFromCookie(request);
        if (refreshToken != null) {
            refreshTokenService.revokeToken(refreshToken);
        }

        // Clear refresh token cookie
        clearRefreshTokenCookie(response);

        // Audit log
        String actorId = authentication != null ? authentication.getName() : "anonymous";
        auditService.logSuccess(AuditEventType.AUTH_LOGOUT, actorId,
                ActorType.USER, null, request);

        if (authentication != null) {
            log.info("User {} logged out", authentication.getName());
        }

        return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully"
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // JWT Minting
    // ─────────────────────────────────────────────────────────────────

    private String mintAccessToken(ArkilUser user, String clientId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenExpiryMinutes, ChronoUnit.MINUTES);

        Set<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toSet());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8080")
                .subject(user.getId().toString())
                .audience(java.util.List.of(clientId))
                .issuedAt(now)
                .expiresAt(expiry)
                .claim("email", user.getEmail())
                .claim("display_name", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .claim("tenant_id", user.getTenant().getId().toString())
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // ─────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────

    private ArkilUser resolveUser(String identifier) {
        // Try email first, then username
        Optional<ArkilUser> userOpt = userRepository.findByEmail(identifier);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }
        return userRepository.findByUsername(identifier).orElse(null);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token, int maxAgeSeconds) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(Boolean.parseBoolean(cookieSecure));
        cookie.setPath("/api/v1/sessions");
        cookie.setMaxAge(maxAgeSeconds);
        response.addCookie(cookie);

        // Add SameSite via Set-Cookie header override
        String sameSiteValue = "None".equalsIgnoreCase(cookieSameSite) ? "None" :
                "Lax".equalsIgnoreCase(cookieSameSite) ? "Lax" : "Strict";
        response.setHeader("Set-Cookie",
                REFRESH_TOKEN_COOKIE + "=" + token +
                        "; Path=/api/v1/sessions" +
                        "; HttpOnly" +
                        (Boolean.parseBoolean(cookieSecure) ? "; Secure" : "") +
                        "; SameSite=" + sameSiteValue +
                        "; Max-Age=" + maxAgeSeconds);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/v1/sessions");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    // ─────────────────────────────────────────────────────────────────
    // Request DTOs
    // ─────────────────────────────────────────────────────────────────

    @Data
    public static class CreateSessionRequest {
        @NotBlank(message = "Identifier (email or username) is required")
        private String identifier;

        // For password auth
        private String password;

        // For magic link auth
        private String magicLinkToken;

        // For TOTP MFA (required if TOTP is enabled for the user)
        private String totpCode;

        // Client context
        private String clientId;
    }
}
