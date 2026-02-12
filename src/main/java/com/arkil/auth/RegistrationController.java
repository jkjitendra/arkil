package com.arkil.auth;

import com.arkil.security.RateLimiterService;
import com.arkil.security.RateLimitBucket;
import com.arkil.user.ArkilUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Registration controller for developer signup.
 * Public endpoint — no authentication required.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Registration", description = "Developer registration for the Arkil platform")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final RateLimiterService rateLimiterService;

    @PostMapping("/register")
    @Operation(summary = "Register a new developer account")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        // Rate limit by IP
        String ip = getClientIp(httpRequest);
        RateLimitBucket.RateLimitResult rateResult = rateLimiterService.checkIp(ip);
        if (!rateResult.isAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(rateResult.getRetryAfterSeconds()))
                    .body(Map.of(
                            "error", "rate_limit_exceeded",
                            "message", "Too many requests. Please try again later."
                    ));
        }

        try {
            ArkilUser user = registrationService.registerDeveloper(
                    request.getEmail(),
                    request.getPassword(),
                    request.getOrgName(),
                    request.getDisplayName()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Account created successfully. Please log in.",
                    "userId", user.getId().toString(),
                    "email", user.getEmail(),
                    "orgName", request.getOrgName()
            ));
        } catch (RegistrationService.RegistrationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "registration_failed",
                    "message", e.getMessage()
            ));
        }
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        @NotBlank(message = "Organization name is required")
        @Size(min = 2, max = 100, message = "Organization name must be between 2 and 100 characters")
        private String orgName;

        @Size(max = 100, message = "Display name must be at most 100 characters")
        private String displayName;
    }

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
