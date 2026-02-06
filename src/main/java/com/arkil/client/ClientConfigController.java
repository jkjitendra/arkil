package com.arkil.client;

import com.arkil.client.dto.ClientPolicyDto;
import com.arkil.client.dto.UpdatePolicyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Client Configuration API for managing authentication policies.
 * Secured with client-credentials grant + arkil:admin scope.
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Tag(name = "Client Config", description = "Client authentication policy management")
@SecurityRequirement(name = "bearerAuth")
public class ClientConfigController {

    private final ClientPolicyService policyService;

    @GetMapping("/{clientId}/policy")
    @Operation(summary = "Get client policy", description = "Retrieve the authentication policy for a client")
    public ResponseEntity<ClientPolicyDto> getPolicy(
            @PathVariable String clientId) {

        ClientAuthPolicy policy = policyService.getPolicyByClientId(clientId)
                .orElseThrow(() -> new PolicyNotFoundException(clientId));

        return ResponseEntity.ok()
                .eTag(String.valueOf(policy.getVersion()))
                .body(ClientPolicyDto.from(policy));
    }

    @PutMapping("/{clientId}/policy")
    @Operation(summary = "Update client policy", description = "Update the authentication policy for a client. Requires If-Match header for optimistic locking.")
    public ResponseEntity<ClientPolicyDto> updatePolicy(
            @PathVariable String clientId,
            @RequestHeader(HttpHeaders.IF_MATCH) @Parameter(description = "Version for optimistic locking") Long ifMatch,
            @Valid @RequestBody UpdatePolicyRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        ClientAuthPolicy policy = policyService.getPolicyByClientId(clientId)
                .orElseThrow(() -> new PolicyNotFoundException(clientId));

        // Optimistic locking check
        if (!policy.getVersion().equals(ifMatch)) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "Policy has been modified. Expected version " + ifMatch + " but found " + policy.getVersion());
        }

        // Update fields
        policy.setEnabledModules(request.getEnabledModules());
        if (request.getModuleConfig() != null) {
            policy.setModuleConfig(request.getModuleConfig());
        }
        if (request.getMfaPolicy() != null) {
            policy.setMfaPolicy(request.getMfaPolicy());
        }
        if (request.getThemeConfig() != null) {
            policy.setThemeConfig(request.getThemeConfig());
        }

        String updatedBy = jwt != null ? jwt.getSubject() : "unknown";
        ClientAuthPolicy saved = policyService.savePolicy(policy, updatedBy);

        return ResponseEntity.ok()
                .eTag(String.valueOf(saved.getVersion()))
                .body(ClientPolicyDto.from(saved));
    }

    @PostMapping("/{clientId}/policy")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create client policy", description = "Create a new authentication policy for a client (deny-all by default)")
    public ResponseEntity<ClientPolicyDto> createPolicy(
            @PathVariable String clientId,
            @RequestParam String registeredClientInternalId,
            @AuthenticationPrincipal Jwt jwt) {

        String createdBy = jwt != null ? jwt.getSubject() : "unknown";
        ClientAuthPolicy policy = policyService.createPolicy(registeredClientInternalId, clientId, createdBy);

        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(String.valueOf(policy.getVersion()))
                .body(ClientPolicyDto.from(policy));
    }

    @GetMapping("/{clientId}/policy/modules/{module}/enabled")
    @Operation(summary = "Check if module is enabled", description = "Check if a specific auth module is enabled for a client")
    public boolean isModuleEnabled(
            @PathVariable String clientId,
            @PathVariable AuthModule module) {
        return policyService.isModuleEnabled(clientId, module);
    }
}
