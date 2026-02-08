package com.arkil.credential.passkey;

import com.arkil.client.AuthModule;
import com.arkil.policy.ClientContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * WebAuthn/Passkey REST API stub.
 * Full implementation requires webauthn4j or Spring Security WebAuthn support.
 */
@RestController
@RequestMapping("/webauthn")
@RequiredArgsConstructor
@Tag(name = "WebAuthn", description = "Passkey/WebAuthn registration and authentication")
public class PasskeyController {

    private final ClientContextHolder clientContextHolder;
    private final PasskeyCredentialRepository passkeyCredentialRepository;

    @PostMapping("/register/options")
    @Operation(summary = "Get registration options", description = "Get WebAuthn registration challenge")
    public ResponseEntity<Map<String, Object>> getRegistrationOptions(@RequestParam UUID userId) {
        checkModuleEnabled();

        // TODO: Implement with webauthn4j
        // This is a stub returning the structure
        return ResponseEntity.ok(Map.of(
                "challenge", java.util.Base64.getEncoder().encodeToString(new byte[32]),
                "rp", Map.of(
                        "name", "Arkil",
                        "id", "localhost"
                ),
                "user", Map.of(
                        "id", userId.toString(),
                        "name", "user@example.com",
                        "displayName", "User"
                ),
                "pubKeyCredParams", java.util.List.of(
                        Map.of("type", "public-key", "alg", -7),
                        Map.of("type", "public-key", "alg", -257)
                ),
                "timeout", 60000,
                "attestation", "none",
                "authenticatorSelection", Map.of(
                        "residentKey", "preferred",
                        "userVerification", "preferred"
                )
        ));
    }

    @PostMapping("/register")
    @Operation(summary = "Complete registration", description = "Verify and store WebAuthn credential")
    public ResponseEntity<Map<String, Object>> completeRegistration(
            @RequestParam UUID userId,
            @RequestBody Map<String, Object> credential) {

        checkModuleEnabled();

        // TODO: Implement credential verification with webauthn4j
        // For now, return a stub response
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Passkey registration is not yet fully implemented",
                "credentialId", "stub-credential-id"
        ));
    }

    @PostMapping("/authenticate/options")
    @Operation(summary = "Get authentication options", description = "Get WebAuthn authentication challenge")
    public ResponseEntity<Map<String, Object>> getAuthenticationOptions(
            @RequestParam(required = false) UUID userId) {

        checkModuleEnabled();

        // TODO: Implement with webauthn4j
        return ResponseEntity.ok(Map.of(
                "challenge", java.util.Base64.getEncoder().encodeToString(new byte[32]),
                "timeout", 60000,
                "rpId", "localhost",
                "userVerification", "preferred",
                "allowCredentials", userId != null ?
                        passkeyCredentialRepository.findByUserId(userId).stream()
                                .map(c -> Map.of(
                                        "type", "public-key",
                                        "id", c.getCredentialId()
                                ))
                                .toList() :
                        java.util.List.of()
        ));
    }

    @PostMapping("/authenticate")
    @Operation(summary = "Complete authentication", description = "Verify WebAuthn assertion")
    public ResponseEntity<Map<String, Object>> completeAuthentication(
            @RequestBody Map<String, Object> assertion) {

        checkModuleEnabled();

        // TODO: Implement assertion verification with webauthn4j
        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Passkey authentication is not yet fully implemented"
        ));
    }

    private void checkModuleEnabled() {
        if (clientContextHolder.hasContext() &&
                !clientContextHolder.getContext().isModuleEnabled(AuthModule.PASSKEY)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Passkey authentication is not enabled for this client");
        }
    }
}
