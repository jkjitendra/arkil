package com.arkil.project;

import com.arkil.client.AuthModule;
import com.arkil.client.ClientAuthPolicy;
import com.arkil.client.ClientAuthPolicyRepository;
import com.arkil.config.DynamicClientRegistrationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Public (no-auth) endpoint for client-side SDK initialization.
 * Returns project configuration: enabled auth methods, configured providers, theme, etc.
 * No secrets are exposed.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public Config", description = "Public project configuration (no auth required)")
public class PublicConfigController {

    private final ApiKeyRepository apiKeyRepository;
    private final ProjectRepository projectRepository;
    private final ClientAuthPolicyRepository policyRepository;
    private final ProjectOAuthProviderRepository providerRepository;
    private final RegisteredClientBridgeService bridgeService;
    private final DynamicClientRegistrationRepository dynamicClientRepo;

    @GetMapping("/config")
    @Operation(summary = "Get public project configuration for client-side SDK initialization")
    public ResponseEntity<?> getConfig(@RequestParam(required = false) String pk,
                                       @RequestParam(required = false, name = "client_id") String clientId) {

        // Resolve project from pk_ or client_id
        Project project = null;

        if (pk != null && !pk.isBlank()) {
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByPublishableKeyWithActiveProject(pk);
            if (apiKeyOpt.isPresent()) {
                project = projectRepository.findById(apiKeyOpt.get().getProjectId()).orElse(null);
            }
        } else if (clientId != null && !clientId.isBlank()) {
            // client_id is like "proj_my-app"
            String slug = clientId.startsWith("proj_") ? clientId.substring(5) : clientId;
            project = projectRepository.findBySlug(slug).orElse(null);
        }

        if (project == null || project.getDeletedAt() != null) {
            return ResponseEntity.notFound().build();
        }

        String resolvedClientId = bridgeService.getClientIdForProject(project.getSlug());
        Optional<ClientAuthPolicy> policyOpt = policyRepository.findByClientId(resolvedClientId);
        if (policyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ClientAuthPolicy policy = policyOpt.get();

        // Get enabled provider names (no secrets)
        List<ProjectOAuthProvider> providers = providerRepository.findByProjectIdAndEnabledTrue(project.getId());
        List<String> enabledProviders = providers.stream()
                .map(ProjectOAuthProvider::getProvider)
                .distinct()
                .toList();

        // Build auth methods list with metadata
        List<AuthMethodInfo> authMethods = policy.getEnabledModules().stream()
                .map(module -> new AuthMethodInfo(
                        module.name(),
                        module.getDisplayName(),
                        isSocialModule(module) ? getProviderForModule(module) : null
                ))
                .toList();

        return ResponseEntity.ok(new PublicConfigResponse(
                project.getName(),
                project.getIconUrl(),
                policy.getThemeConfig(),
                authMethods,
                enabledProviders
        ));
    }

    private boolean isSocialModule(AuthModule module) {
        return module == AuthModule.OAUTH2_GOOGLE || module == AuthModule.OAUTH2_GITHUB ||
               module == AuthModule.OAUTH2_APPLE || module == AuthModule.OAUTH2_LINKEDIN ||
               module == AuthModule.OAUTH2_CUSTOM_OIDC;
    }

    private String getProviderForModule(AuthModule module) {
        return switch (module) {
            case OAUTH2_GOOGLE -> "google";
            case OAUTH2_GITHUB -> "github";
            case OAUTH2_APPLE -> "apple";
            case OAUTH2_LINKEDIN -> "linkedin";
            case OAUTH2_CUSTOM_OIDC -> "custom-oidc";
            default -> null;
        };
    }

    @GetMapping("/platform-providers")
    @Operation(summary = "Get available platform-level social login providers (for dashboard login)")
    public ResponseEntity<List<String>> getPlatformProviders() {
        return ResponseEntity.ok(List.copyOf(dynamicClientRepo.getAvailablePlatformProviders()));
    }

    // ─────────────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────────────

    public record PublicConfigResponse(
            String projectName,
            String iconUrl,
            String themeConfig,
            List<AuthMethodInfo> authMethods,
            List<String> enabledProviders
    ) {}

    public record AuthMethodInfo(
            String module,
            String displayName,
            String provider
    ) {}
}
