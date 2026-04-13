package com.arkil.session;

import com.arkil.credential.passkey.PasskeyCredential;
import com.arkil.credential.passkey.PasskeyCredentialRepository;
import com.arkil.credential.totp.TotpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Factor API controller for MFA management (TOTP, Passkey).
 * Part of the Session/UX API surface (Surface B).
 *
 * Note: Passkey can be primary auth OR second factor.
 * TOTP is typically second factor only.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/factors")
@RequiredArgsConstructor
@Tag(name = "Factors", description = "Multi-factor authentication management")
@SecurityRequirement(name = "bearerAuth")
public class FactorController {

    private final TotpService totpService;
    private final PasskeyCredentialRepository passkeyCredentialRepository;

    @Value("${arkil.mfa.totp.issuer:Arkil}")
    private String totpIssuer;

    // ─────────────────────────────────────────────────────────────────
    // TOTP Factor
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/totp")
    @Operation(summary = "Start TOTP enrollment (get QR code)")
    public ResponseEntity<Map<String, Object>> enrollTotp(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        log.info("User {} starting TOTP enrollment", userId);

        try {
            TotpService.TotpEnrollmentResponse enrollment = totpService.startEnrollment(userId, totpIssuer);

            return ResponseEntity.ok(Map.of(
                    "secret", enrollment.secret(),
                    "qrCodeUri", enrollment.provisioningUri(),
                    "algorithm", "SHA1",
                    "digits", 6,
                    "period", 30,
                    "message", "Scan QR code with authenticator app, then verify with POST /api/v1/factors/totp/verify"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "enrollment_failed",
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/totp/verify")
    @Operation(summary = "Verify TOTP code and activate factor")
    public ResponseEntity<Map<String, Object>> verifyAndActivateTotp(
            Authentication authentication,
            @Valid @RequestBody TotpVerifyRequest request) {

        UUID userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        log.info("User {} verifying TOTP code", userId);

        try {
            boolean confirmed = totpService.confirmEnrollment(userId, request.getCode());

            if (confirmed) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "TOTP factor activated"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Invalid TOTP code. Please try again."
                ));
            }
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "TOTP not enrolled. Start enrollment first."
            ));
        }
    }

    @GetMapping("/totp/status")
    @Operation(summary = "Check TOTP enrollment status")
    public ResponseEntity<Map<String, Object>> getTotpStatus(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        boolean enabled = totpService.isEnabled(userId);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @DeleteMapping("/totp")
    @Operation(summary = "Remove TOTP factor")
    public ResponseEntity<Map<String, String>> removeTotp(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        log.info("User {} removing TOTP factor", userId);
        totpService.remove(userId);

        return ResponseEntity.ok(Map.of(
                "message", "TOTP factor removed"
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Passkey Factor (WebAuthn) — listing and removal
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/passkey")
    @Operation(summary = "List registered passkeys")
    public ResponseEntity<Map<String, Object>> listPasskeys(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        List<PasskeyCredential> passkeys = passkeyCredentialRepository.findByUserId(userId);
        List<Map<String, Object>> passkeyList = passkeys.stream()
                .map(pk -> Map.<String, Object>of(
                        "id", pk.getId().toString(),
                        "credentialId", pk.getCredentialId(),
                        "label", pk.getLabel() != null ? pk.getLabel() : "Passkey",
                        "createdAt", pk.getCreatedAt().toString(),
                        "lastUsedAt", pk.getLastUsedAt() != null ? pk.getLastUsedAt().toString() : ""
                ))
                .toList();

        return ResponseEntity.ok(Map.of("passkeys", passkeyList));
    }

    @DeleteMapping("/passkey/{credentialId}")
    @Operation(summary = "Remove a passkey")
    public ResponseEntity<Map<String, String>> removePasskey(
            Authentication authentication,
            @PathVariable String credentialId) {

        UUID userId = extractUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        log.info("User {} removing passkey: {}", userId, credentialId);
        passkeyCredentialRepository.deleteByUserIdAndCredentialId(userId, credentialId);

        return ResponseEntity.ok(Map.of(
                "message", "Passkey removed"
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private UUID extractUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Request DTOs
    // ─────────────────────────────────────────────────────────────────

    @Data
    public static class TotpVerifyRequest {
        @NotBlank(message = "TOTP code is required")
        private String code;
    }
}
