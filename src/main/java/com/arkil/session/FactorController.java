package com.arkil.session;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    // TODO: Inject TotpService and PasskeyService

    // ─────────────────────────────────────────────────────────────────
    // TOTP Factor
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/totp")
    @Operation(summary = "Start TOTP enrollment (get QR code)")
    public ResponseEntity<Map<String, Object>> enrollTotp(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String username = authentication.getName();
        log.info("User {} starting TOTP enrollment", username);

        // TODO: Generate TOTP secret and QR code via TotpService
        // For now, return mock data
        String mockSecret = "JBSWY3DPEHPK3PXP";
        String mockQrUri = "otpauth://totp/Arkil:" + username + "?secret=" + mockSecret + "&issuer=Arkil";

        return ResponseEntity.ok(Map.of(
                "secret", mockSecret,
                "qrCodeUri", mockQrUri,
                "algorithm", "SHA1",
                "digits", 6,
                "period", 30,
                "message", "Scan QR code with authenticator app, then verify with POST /api/v1/factors/totp/verify"
        ));
    }

    @PostMapping("/totp/verify")
    @Operation(summary = "Verify TOTP code and activate factor")
    public ResponseEntity<Map<String, Object>> verifyAndActivateTotp(
            Authentication authentication,
            @Valid @RequestBody TotpVerifyRequest request) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String username = authentication.getName();
        log.info("User {} verifying TOTP code", username);

        // TODO: Verify code with TotpService and activate
        // For now, mock success
        boolean valid = request.getCode() != null && request.getCode().length() == 6;

        if (valid) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "TOTP factor activated",
                    "backupCodes", java.util.List.of(
                            "AAAA-BBBB-CCCC",
                            "DDDD-EEEE-FFFF",
                            "GGGG-HHHH-IIII",
                            "JJJJ-KKKK-LLLL",
                            "MMMM-NNNN-OOOO"
                    )
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid TOTP code"
            ));
        }
    }

    @DeleteMapping("/totp")
    @Operation(summary = "Remove TOTP factor")
    public ResponseEntity<Map<String, String>> removeTotp(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        log.info("User {} removing TOTP factor", authentication.getName());
        // TODO: Remove TOTP credential via TotpService

        return ResponseEntity.ok(Map.of(
                "message", "TOTP factor removed"
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Passkey Factor (WebAuthn)
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/passkey/options")
    @Operation(summary = "Get WebAuthn creation options for new passkey")
    public ResponseEntity<Map<String, Object>> getPasskeyCreationOptions(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        String username = authentication.getName();
        log.info("User {} requesting passkey creation options", username);

        // TODO: Generate WebAuthn PublicKeyCredentialCreationOptions
        // This requires a WebAuthn library (webauthn4j or similar)

        return ResponseEntity.ok(Map.of(
                "challenge", java.util.Base64.getUrlEncoder().encodeToString(UUID.randomUUID().toString().getBytes()),
                "rp", Map.of(
                        "name", "Arkil",
                        "id", "localhost"
                ),
                "user", Map.of(
                        "id", java.util.Base64.getUrlEncoder().encodeToString(username.getBytes()),
                        "name", username,
                        "displayName", username
                ),
                "pubKeyCredParams", java.util.List.of(
                        Map.of("alg", -7, "type", "public-key"),  // ES256
                        Map.of("alg", -257, "type", "public-key") // RS256
                ),
                "authenticatorSelection", Map.of(
                        "residentKey", "preferred",
                        "userVerification", "preferred"
                ),
                "timeout", 60000,
                "attestation", "none"
        ));
    }

    @PostMapping("/passkey/verify")
    @Operation(summary = "Verify and register passkey credential")
    public ResponseEntity<Map<String, Object>> registerPasskey(
            Authentication authentication,
            @Valid @RequestBody PasskeyRegistrationRequest request) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        log.info("User {} registering passkey", authentication.getName());

        // TODO: Verify attestation response and store credential
        // This requires webauthn4j library

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Passkey registered successfully",
                "credentialId", UUID.randomUUID().toString()
        ));
    }

    @GetMapping("/passkey")
    @Operation(summary = "List registered passkeys")
    public ResponseEntity<Map<String, Object>> listPasskeys(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        // TODO: Fetch passkeys from PasskeyCredentialRepository

        return ResponseEntity.ok(Map.of(
                "passkeys", java.util.List.of()
        ));
    }

    @DeleteMapping("/passkey/{credentialId}")
    @Operation(summary = "Remove a passkey")
    public ResponseEntity<Map<String, String>> removePasskey(
            Authentication authentication,
            @PathVariable String credentialId) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        log.info("User {} removing passkey: {}", authentication.getName(), credentialId);
        // TODO: Delete passkey from PasskeyCredentialRepository

        return ResponseEntity.ok(Map.of(
                "message", "Passkey removed"
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Request DTOs
    // ─────────────────────────────────────────────────────────────────

    @Data
    public static class TotpVerifyRequest {
        @NotBlank(message = "TOTP code is required")
        private String code;
    }

    @Data
    public static class PasskeyRegistrationRequest {
        @NotBlank(message = "Credential response is required")
        private String credentialResponse;
    }
}
