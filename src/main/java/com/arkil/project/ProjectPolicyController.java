package com.arkil.project;

import com.arkil.client.AuthModule;
import com.arkil.client.ClientAuthPolicy;
import com.arkil.client.ClientAuthPolicyRepository;
import com.arkil.security.SecretEncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * API controller for managing per-project authentication methods and OAuth providers.
 * Developers use these endpoints to toggle auth methods (email/password, Google, GitHub, etc.)
 * and configure their own OAuth2 provider credentials for end-user social login.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
@Tag(name = "Project Auth Policy", description = "Auth method and OAuth provider configuration")
@SecurityRequirement(name = "bearerAuth")
public class ProjectPolicyController {

    private final ProjectService projectService;
    private final ClientAuthPolicyRepository policyRepository;
    private final ProjectOAuthProviderRepository providerRepository;
    private final RegisteredClientBridgeService bridgeService;
    private final SecretEncryptionService encryptionService;

    // ─────────────────────────────────────────────────────────────────
    // Auth Methods (module toggles)
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/auth-methods")
    @Operation(summary = "Get enabled auth methods and configured providers for a project")
    public ResponseEntity<?> getAuthMethods(
            @PathVariable UUID projectId,
            Authentication auth) {

        Project project = verifyOwnership(projectId, auth);
        if (project == null) return ResponseEntity.notFound().build();

        String clientId = bridgeService.getClientIdForProject(project.getSlug());
        Optional<ClientAuthPolicy> policyOpt = policyRepository.findByClientId(clientId);
        if (policyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ClientAuthPolicy policy = policyOpt.get();
        List<ProjectOAuthProvider> providers = providerRepository.findByProjectId(projectId);

        return ResponseEntity.ok(new AuthMethodsResponse(
                policy.getEnabledModules().stream()
                        .map(AuthModule::name)
                        .collect(Collectors.toSet()),
                providers.stream()
                        .map(p -> new OAuthProviderSummary(
                                p.getProvider(),
                                p.getClientId(),
                                p.getEnvironment().name(),
                                p.isEnabled()))
                        .toList()
        ));
    }

    @PutMapping("/auth-methods")
    @Operation(summary = "Update enabled auth methods for a project")
    @Transactional
    public ResponseEntity<?> updateAuthMethods(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateAuthMethodsRequest request,
            Authentication auth) {

        Project project = verifyOwnership(projectId, auth);
        if (project == null) return ResponseEntity.notFound().build();

        String clientId = bridgeService.getClientIdForProject(project.getSlug());
        Optional<ClientAuthPolicy> policyOpt = policyRepository.findByClientId(clientId);
        if (policyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Validate module names
        Set<AuthModule> modules = new HashSet<>();
        for (String moduleName : request.getEnabledModules()) {
            try {
                modules.add(AuthModule.valueOf(moduleName));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "invalid_module",
                        "message", "Unknown auth module: " + moduleName
                ));
            }
        }

        // Validate: social modules require configured provider credentials
        for (AuthModule module : modules) {
            if (isSocialModule(module)) {
                String provider = getProviderForModule(module);
                List<ProjectOAuthProvider> configured = providerRepository.findByProjectId(projectId)
                        .stream()
                        .filter(p -> p.getProvider().equals(provider) && p.isEnabled())
                        .toList();
                if (configured.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "provider_not_configured",
                            "message", module.getDisplayName() + " requires provider credentials to be configured first"
                    ));
                }
            }
        }

        ClientAuthPolicy policy = policyOpt.get();
        policy.setEnabledModules(modules);
        policyRepository.save(policy);

        log.info("Updated auth methods for project {}: {}", project.getSlug(), modules);

        return ResponseEntity.ok(Map.of(
                "enabledModules", modules.stream().map(AuthModule::name).collect(Collectors.toSet())
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // OAuth Providers (credential management)
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/oauth-providers")
    @Operation(summary = "List configured OAuth providers for a project")
    public ResponseEntity<?> listOAuthProviders(
            @PathVariable UUID projectId,
            Authentication auth) {

        Project project = verifyOwnership(projectId, auth);
        if (project == null) return ResponseEntity.notFound().build();

        List<ProjectOAuthProvider> providers = providerRepository.findByProjectId(projectId);

        return ResponseEntity.ok(providers.stream()
                .map(p -> new OAuthProviderDto(
                        p.getId(),
                        p.getProvider(),
                        p.getClientId(),
                        maskSecret(p.getClientSecretEncrypted()),
                        p.getScopes(),
                        p.getEnvironment().name(),
                        p.isEnabled(),
                        p.getCreatedAt().toString(),
                        p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null
                ))
                .toList());
    }

    @PostMapping("/oauth-providers")
    @Operation(summary = "Add or update an OAuth provider configuration")
    @Transactional
    public ResponseEntity<?> upsertOAuthProvider(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpsertOAuthProviderRequest request,
            Authentication auth) {

        Project project = verifyOwnership(projectId, auth);
        if (project == null) return ResponseEntity.notFound().build();

        // Validate provider name
        if (!isValidProvider(request.getProvider())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_provider",
                    "message", "Unsupported provider: " + request.getProvider() +
                            ". Supported: google, github, apple, linkedin"
            ));
        }

        Project.Environment env = request.getEnvironment() != null
                ? Project.Environment.valueOf(request.getEnvironment())
                : project.getEnvironment();

        // Upsert: find existing or create new
        Optional<ProjectOAuthProvider> existingProvider = providerRepository
                .findByProjectIdAndProviderAndEnvironment(projectId, request.getProvider(), env);

        boolean isCreate = existingProvider.isEmpty();
        if (isCreate && !StringUtils.hasText(request.getClientSecret())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_client_secret",
                    "message", "Client secret is required when creating a provider configuration"
            ));
        }

        ProjectOAuthProvider provider = existingProvider
                .orElseGet(() -> ProjectOAuthProvider.builder()
                        .projectId(projectId)
                        .provider(request.getProvider())
                        .environment(env)
                        .build());

        provider.setClientId(request.getClientId());
        if (StringUtils.hasText(request.getClientSecret())) {
            provider.setClientSecretEncrypted(encryptionService.encrypt(request.getClientSecret().trim()));
        }
        provider.setScopes(request.getScopes() != null ? request.getScopes() : getDefaultScopes(request.getProvider()));
        provider.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);

        providerRepository.save(provider);

        log.info("Upserted OAuth provider {} for project {} ({})", request.getProvider(), project.getSlug(), env);

        return ResponseEntity.status(HttpStatus.OK).body(new OAuthProviderDto(
                provider.getId(),
                provider.getProvider(),
                provider.getClientId(),
                maskSecret(provider.getClientSecretEncrypted()),
                provider.getScopes(),
                provider.getEnvironment().name(),
                provider.isEnabled(),
                provider.getCreatedAt().toString(),
                provider.getUpdatedAt() != null ? provider.getUpdatedAt().toString() : null
        ));
    }

    @DeleteMapping("/oauth-providers/{provider}")
    @Operation(summary = "Remove an OAuth provider configuration")
    @Transactional
    public ResponseEntity<?> deleteOAuthProvider(
            @PathVariable UUID projectId,
            @PathVariable String provider,
            @RequestParam(required = false) String environment,
            Authentication auth) {

        Project project = verifyOwnership(projectId, auth);
        if (project == null) return ResponseEntity.notFound().build();

        Project.Environment env = environment != null
                ? Project.Environment.valueOf(environment)
                : project.getEnvironment();

        if (!providerRepository.existsByProjectIdAndProviderAndEnvironment(projectId, provider, env)) {
            return ResponseEntity.notFound().build();
        }

        // If the corresponding social module is enabled, disable it first
        String clientId = bridgeService.getClientIdForProject(project.getSlug());
        policyRepository.findByClientId(clientId).ifPresent(policy -> {
            AuthModule module = getModuleForProvider(provider);
            if (module != null && policy.getEnabledModules().contains(module)) {
                policy.getEnabledModules().remove(module);
                policyRepository.save(policy);
                log.info("Auto-disabled {} because provider credentials were removed", module);
            }
        });

        providerRepository.deleteByProjectIdAndProviderAndEnvironment(projectId, provider, env);

        log.info("Deleted OAuth provider {} for project {} ({})", provider, project.getSlug(), env);
        return ResponseEntity.ok(Map.of("message", "Provider removed"));
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private Project verifyOwnership(UUID projectId, Authentication auth) {
        UUID tenantId = getTenantId(auth);

        if (tenantId != null) {
            // Tenant-scoped access check
            return projectService.getProjectForTenant(projectId, tenantId).orElse(null);
        }

        // Fallback: owner-based check
        UUID ownerId = getOwnerId(auth);
        return projectService.getProject(projectId)
                .filter(p -> p.getOwnerId().equals(ownerId) || hasAdminRole(auth))
                .orElse(null);
    }

    private UUID getOwnerId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null) {
                try { return UUID.fromString(sub); } catch (IllegalArgumentException ignored) {}
            }
        }
        String name = auth.getName();
        try { return UUID.fromString(name); } catch (IllegalArgumentException ignored) {}
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private UUID getTenantId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String tenantIdStr = jwt.getClaimAsString("tenant_id");
            if (tenantIdStr != null) {
                try { return UUID.fromString(tenantIdStr); } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    private boolean hasAdminRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_arkil:super-admin") ||
                               a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    private boolean isSocialModule(AuthModule module) {
        return module == AuthModule.OAUTH2_GOOGLE || module == AuthModule.OAUTH2_GITHUB ||
               module == AuthModule.OAUTH2_APPLE || module == AuthModule.OAUTH2_LINKEDIN;
    }

    private String getProviderForModule(AuthModule module) {
        return switch (module) {
            case OAUTH2_GOOGLE -> "google";
            case OAUTH2_GITHUB -> "github";
            case OAUTH2_APPLE -> "apple";
            case OAUTH2_LINKEDIN -> "linkedin";
            default -> null;
        };
    }

    private AuthModule getModuleForProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "google" -> AuthModule.OAUTH2_GOOGLE;
            case "github" -> AuthModule.OAUTH2_GITHUB;
            case "apple" -> AuthModule.OAUTH2_APPLE;
            case "linkedin" -> AuthModule.OAUTH2_LINKEDIN;
            default -> null;
        };
    }

    private boolean isValidProvider(String provider) {
        return Set.of("google", "github", "apple", "linkedin").contains(provider.toLowerCase());
    }

    private String getDefaultScopes(String provider) {
        return switch (provider.toLowerCase()) {
            case "google" -> "openid,profile,email";
            case "github" -> "read:user,user:email";
            case "apple" -> "openid,name,email";
            case "linkedin" -> "openid,profile,email";
            default -> "openid,profile,email";
        };
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.length() < 8) return "****";
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }

    // ─────────────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────────────

    public record AuthMethodsResponse(
            Set<String> enabledModules,
            List<OAuthProviderSummary> configuredProviders
    ) {}

    public record OAuthProviderSummary(
            String provider,
            String clientId,
            String environment,
            boolean enabled
    ) {}

    public record OAuthProviderDto(
            UUID id,
            String provider,
            String clientId,
            String clientSecretMasked,
            String scopes,
            String environment,
            boolean enabled,
            String createdAt,
            String updatedAt
    ) {}

    @Data
    public static class UpdateAuthMethodsRequest {
        @NotNull(message = "enabledModules is required")
        private Set<String> enabledModules;
    }

    @Data
    public static class UpsertOAuthProviderRequest {
        @NotBlank(message = "Provider is required")
        private String provider;

        @NotBlank(message = "Client ID is required")
        private String clientId;

        private String clientSecret;

        private String scopes;
        private String environment;
        private Boolean enabled;
    }
}
