package com.arkil.session;

import com.arkil.email.EmailTokenService;
import com.arkil.security.RateLimitBucket;
import com.arkil.security.RateLimiterService;
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
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        // TODO: Implement password reset with token
        // This requires PasswordCredentialService to update the password

        return ResponseEntity.ok(Map.of(
                "message", "Password reset functionality coming soon"
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
        // TODO: Look up user by email and send verification
        // Similar to forgot-password, don't reveal if user exists

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
    @Operation(summary = "Verify magic link and create session")
    public ResponseEntity<Map<String, Object>> verifyMagicLink(@RequestParam String token) {
        // TODO: Verify magic link token and create session
        // This will need to integrate with session management

        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Magic link authentication coming soon"
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
