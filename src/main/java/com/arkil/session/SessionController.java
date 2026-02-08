package com.arkil.session;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

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
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "Session management (login, refresh, logout)")
public class SessionController {

    private final RefreshTokenService refreshTokenService;

    @Value("${arkil.session.cookie.same-site:None}")
    private String cookieSameSite;

    @Value("${arkil.session.cookie.secure:true}")
    private String cookieSecure;

    @Value("${arkil.token.refresh.expiry-days:30}")
    private int refreshTokenExpiryDays;

    private static final String REFRESH_TOKEN_COOKIE = "arkil_refresh_token";

    // ─────────────────────────────────────────────────────────────────
    // Session Creation (Login)
    // ─────────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create session (login with password or passkey)")
    public ResponseEntity<Map<String, Object>> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            HttpServletResponse response) {

        log.info("Session creation request for identifier: {}", request.getIdentifier());

        // TODO: Integrate with authentication providers
        // 1. Look up user by identifier (email/username)
        // 2. Validate credential (password or passkey assertion)
        // 3. Check MFA requirements
        // 4. Generate tokens via RefreshTokenService.issueRefreshToken()

        // For now, return mock response structure
        return ResponseEntity.ok(Map.of(
                "message", "Session creation via API coming soon",
                "note", "Use OIDC /oauth2/authorize flow for now"
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

        // TODO: Generate new access token (JWT)
        // For now, just return success with token ID
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Token refreshed successfully",
                "tokenId", newTokenPair.entity().getId().toString(),
                "expiresAt", newTokenPair.entity().getExpiresAt().toString()
                // TODO: Add "accessToken" field with actual JWT
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────

    @DeleteMapping("/current")
    @Operation(summary = "Logout (delete current session)")
    public ResponseEntity<Map<String, String>> logout(
            Authentication authentication,
            HttpServletResponse response) {

        if (authentication != null) {
            log.info("User {} logging out", authentication.getName());
        }

        // Clear refresh token cookie
        clearRefreshTokenCookie(response);

        // TODO: Revoke tokens in database
        // TODO: Record logout in audit log

        return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully"
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────

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
        // SameSite attribute set via response header (not directly supported in Cookie)
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

        // For passkey auth
        private String passkeyAssertion;

        // For magic link auth
        private String magicLinkToken;

        // Client context
        private String clientId;
    }
}
