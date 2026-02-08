package com.arkil.credential.totp;

import com.arkil.client.AuthModule;
import com.arkil.policy.ClientContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * REST API for TOTP enrollment and verification.
 */
@RestController
@RequestMapping("/totp")
@RequiredArgsConstructor
@Tag(name = "TOTP", description = "Time-based OTP enrollment and verification")
public class TotpController {

    private final TotpService totpService;
    private final ClientContextHolder clientContextHolder;

    @PostMapping("/enroll")
    @Operation(summary = "Start TOTP enrollment", description = "Generates a new TOTP secret and QR code URI")
    public ResponseEntity<Map<String, String>> startEnrollment(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "Arkil") String issuer) {

        checkModuleEnabled();

        TotpService.TotpEnrollmentResponse response = totpService.startEnrollment(userId, issuer);

        return ResponseEntity.ok(Map.of(
                "secret", response.secret(),
                "provisioningUri", response.provisioningUri()
        ));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm TOTP enrollment", description = "Verify the code to enable TOTP")
    public ResponseEntity<Map<String, Object>> confirmEnrollment(
            @RequestParam UUID userId,
            @RequestParam String code) {

        checkModuleEnabled();

        boolean valid = totpService.confirmEnrollment(userId, code);

        return ResponseEntity.ok(Map.of(
                "success", valid,
                "message", valid ? "TOTP enabled successfully" : "Invalid code"
        ));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify TOTP code", description = "Verify a TOTP code for MFA")
    public ResponseEntity<Map<String, Object>> verify(
            @RequestParam UUID userId,
            @RequestParam String code) {

        checkModuleEnabled();

        boolean valid = totpService.verify(userId, code);

        return ResponseEntity.ok(Map.of(
                "valid", valid
        ));
    }

    @GetMapping("/status")
    @Operation(summary = "Check TOTP status", description = "Check if user has TOTP enabled")
    public ResponseEntity<Map<String, Object>> status(@RequestParam UUID userId) {
        checkModuleEnabled();

        boolean enabled = totpService.isEnabled(userId);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "totpEnabled", enabled
        ));
    }

    private void checkModuleEnabled() {
        if (clientContextHolder.hasContext() &&
                !clientContextHolder.getContext().isModuleEnabled(AuthModule.TOTP)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "TOTP authentication is not enabled for this client");
        }
    }
}
