package com.arkil.session;

import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.email.EmailToken;
import com.arkil.email.EmailTokenService;
import com.arkil.security.RateLimitBucket;
import com.arkil.security.RateLimiterService;
import com.arkil.tenant.TenantContext;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Auth API controller for password reset, email verification, and magic links.
 * Part of the Session/UX API surface (Surface B).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication flows (password reset, email verification)")
public class AuthController {

    private final EmailTokenService emailTokenService;
    private final RateLimiterService rateLimiterService;
    private final UserRepository userRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    // ─────────────────────────────────────────────────────────────────
    // Password Reset
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset email")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        // Rate limit by IP and email
        String ip = getClientIp(httpRequest);
        RateLimitBucket.RateLimitResult result = rateLimiterService.checkAuthRequest(ip, request.getEmail(), null);

        if (!result.isAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Limit", String.valueOf(result.getLimit()))
                    .header("X-RateLimit-Remaining", String.valueOf(result.getRemaining()))
                    .header("Retry-After", String.valueOf(result.getRetryAfterSeconds()))
                    .body(Map.of(
                            "error", "rate_limit_exceeded",
                            "message", "Too many requests. Please try again later.",
                            "retryAfter", result.getRetryAfterSeconds()
                    ));
        }

        // Always return success to prevent email enumeration
        emailTokenService.sendPasswordResetEmail(request.getEmail());

        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(result.getLimit()))
                .header("X-RateLimit-Remaining", String.valueOf(result.getRemaining()))
                .body(Map.of(
                        "message", "If an account exists with that email, a reset link has been sent."
                ));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with token")
    public ResponseEntity<Map<String, Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        Optional<EmailToken> tokenOpt = emailTokenService.verifyToken(
                request.getToken(), EmailToken.TokenType.PASSWORD_RESET);

        if (tokenOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "invalid_token",
                    "message", "Invalid or expired reset token. Please request a new one."
            ));
        }

        EmailToken emailToken = tokenOpt.get();
        ArkilUser user = userRepository.findById(emailToken.getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "user_not_found",
                    "message", "User not found."
            ));
        }

        // Update or create password credential
        Optional<PasswordCredential> credOpt = passwordCredentialRepository.findByUser_Id(user.getId());
        if (credOpt.isPresent()) {
            PasswordCredential cred = credOpt.get();
            cred.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            passwordCredentialRepository.save(cred);
        } else {
            passwordCredentialRepository.save(PasswordCredential.builder()
                    .user(user)
                    .passwordHash(passwordEncoder.encode(request.getNewPassword()))
                    .algorithm("bcrypt")
                    .build());
        }

        log.info("Password reset via API for user: {}", user.getEmail());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password has been reset successfully."
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Email Verification
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email with token")
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestParam String token) {
        boolean verified = emailTokenService.verifyEmail(token);

        if (verified) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Email verified successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid or expired verification token"
            ));
        }
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        Optional<ArkilUser> userOpt = tenantId != null
                ? userRepository.findByTenantIdAndEmail(tenantId, request.getEmail())
                : userRepository.findByEmail(request.getEmail());

        userOpt.ifPresent(user -> {
            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                emailTokenService.sendVerificationEmail(user.getId());
            }
        });

        // Always return success to prevent email enumeration
        return ResponseEntity.ok(Map.of(
                "message", "If an account exists with that email, a verification link has been sent."
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Magic Link (Passwordless)
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/magic-link")
    @Operation(summary = "Request magic link for passwordless login")
    public ResponseEntity<Map<String, String>> requestMagicLink(@Valid @RequestBody MagicLinkRequest request) {
        emailTokenService.sendMagicLink(request.getEmail());

        return ResponseEntity.ok(Map.of(
                "message", "If an account exists with that email, a sign-in link has been sent."
        ));
    }

    @GetMapping("/magic-link")
    @Operation(summary = "Verify magic link token")
    public ResponseEntity<Map<String, Object>> verifyMagicLink(@RequestParam String token) {
        Optional<EmailToken> tokenOpt = emailTokenService.verifyToken(token, EmailToken.TokenType.MAGIC_LINK);

        if (tokenOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "invalid_token",
                    "message", "Invalid or expired magic link."
            ));
        }

        EmailToken emailToken = tokenOpt.get();
        ArkilUser user = userRepository.findById(emailToken.getUserId()).orElse(null);
        if (user == null || !user.getEnabled()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "user_not_found",
                    "message", "Account not found or disabled."
            ));
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "error", "email_not_verified",
                    "message", "Verify your email address before signing in.",
                    "email", user.getEmail(),
                    "canResendVerification", true,
                    "resendVerificationPath", "/api/v1/auth/resend-verification"
            ));
        }

        log.info("Magic link verified via API for user: {}", user.getEmail());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "message", "Magic link verified. Use the OIDC flow to obtain tokens."
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Request DTOs
    // ─────────────────────────────────────────────────────────────────

    @Data
    public static class ForgotPasswordRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "Token is required")
        private String token;

        @NotBlank(message = "New password is required")
        private String newPassword;
    }

    @Data
    public static class ResendVerificationRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    public static class MagicLinkRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
    }

    // ─────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
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
}
