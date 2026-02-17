package com.arkil.project;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Management API controller for projects.
 * Part of Surface C (Admin/Dashboard APIs).
 * 
 * Requires: admin OAuth token or sk_ secret key
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project management (admin)")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final ApiKeyService apiKeyService;
    private final RegisteredClientBridgeService registeredClientBridge;
    private final UrlValidator urlValidator;
    private final org.springframework.core.env.Environment env;

    // ─────────────────────────────────────────────────────────────────
    // Project CRUD
    // ─────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List projects")
    public ResponseEntity<List<ProjectDto>> listProjects(Authentication auth) {
        UUID tenantId = getTenantId(auth);
        List<Project> projects;

        if (tenantId != null) {
            projects = projectService.listProjectsByTenant(tenantId);
        } else {
            projects = projectService.listProjects(getOwnerId(auth));
        }

        return ResponseEntity.ok(projects.stream()
                .map(this::toDto)
                .toList());
    }

    @PostMapping
    @Operation(summary = "Create a new project")
    public ResponseEntity<?> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            Authentication auth) {

        UUID ownerId = getOwnerId(auth);
        
        // Validate origins and redirect URIs
        boolean isDev = request.getEnvironment() == null || 
                        request.getEnvironment() == Project.Environment.DEVELOPMENT;
        
        UrlValidator.ValidationResult originResult = urlValidator.validateOrigins(
                request.getAllowedOrigins(), isDev);
        if (!originResult.valid()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_origins",
                    "messages", originResult.errors()
            ));
        }
        
        UrlValidator.ValidationResult redirectResult = urlValidator.validateRedirectUris(
                request.getRedirectUris(), isDev);
        if (!redirectResult.valid()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_redirect_uris",
                    "messages", redirectResult.errors()
            ));
        }

        UUID tenantId = getTenantId(auth);

        ProjectService.ProjectWithKeys result = projectService.createProject(
                new ProjectService.CreateProjectRequest(
                        request.getName(),
                        request.getSlug(),
                        request.getDescription(),
                        request.getEnvironment(),
                        request.getAllowedOrigins(),
                        request.getRedirectUris()
                ),
                ownerId,
                tenantId
        );

        log.info("Project created: {}", result.project().getSlug());

        return ResponseEntity.status(HttpStatus.CREATED).body(new ProjectCreatedResponse(
                toDto(result.project()),
                toKeyDto(result.apiKey()),
                result.secretKeyPlaintext() // Shown only once!
        ));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<?> getProject(@PathVariable UUID projectId, Authentication auth) {
        return verifyProjectAccess(projectId, auth)
                .map(p -> ResponseEntity.ok(toDto(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update project")
    public ResponseEntity<?> updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            Authentication auth) {

        // Verify tenant membership
        Project existing = verifyProjectAccess(projectId, auth).orElse(null);
        
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Validate origins and redirect URIs
        boolean isDev = (request.getEnvironment() != null ? request.getEnvironment() : existing.getEnvironment()) 
                        == Project.Environment.DEVELOPMENT;
        
        if (request.getAllowedOrigins() != null) {
            UrlValidator.ValidationResult originResult = urlValidator.validateOrigins(
                    request.getAllowedOrigins(), isDev);
            if (!originResult.valid()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "invalid_origins",
                        "messages", originResult.errors()
                ));
            }
        }
        
        if (request.getRedirectUris() != null) {
            UrlValidator.ValidationResult redirectResult = urlValidator.validateRedirectUris(
                    request.getRedirectUris(), isDev);
            if (!redirectResult.valid()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "invalid_redirect_uris",
                        "messages", redirectResult.errors()
                ));
            }
        }

        Project updated = projectService.updateProject(projectId,
                new ProjectService.UpdateProjectRequest(
                        request.getName(),
                        request.getDescription(),
                        request.getIconUrl(),
                        request.getEnvironment(),
                        request.getAllowedOrigins(),
                        request.getRedirectUris()
                ));

        return ResponseEntity.ok(toDto(updated));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete project (soft delete)")
    public ResponseEntity<?> deleteProject(@PathVariable UUID projectId, Authentication auth) {
        // Verify tenant membership
        Project existing = verifyProjectAccess(projectId, auth).orElse(null);

        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        projectService.deleteProject(projectId);
        return ResponseEntity.ok(Map.of("message", "Project deleted"));
    }

    @GetMapping("/deleted")
    @Operation(summary = "List deleted projects (can be restored within 7 days)")
    public ResponseEntity<List<DeletedProjectDto>> listDeletedProjects(Authentication auth) {
        UUID tenantId = getTenantId(auth);
        List<Project> deletedProjects;

        if (tenantId != null) {
            deletedProjects = projectService.listDeletedProjectsByTenant(tenantId);
        } else {
            deletedProjects = projectService.listDeletedProjects(getOwnerId(auth));
        }

        return ResponseEntity.ok(deletedProjects.stream()
                .map(this::toDeletedDto)
                .toList());
    }

    @PostMapping("/{projectId}/restore")
    @Operation(summary = "Restore a deleted project (within 7 days)")
    public ResponseEntity<?> restoreProject(@PathVariable UUID projectId, Authentication auth) {
        // Verify tenant membership (includes deleted projects)
        UUID tenantId = getTenantId(auth);
        Project existing;

        if (tenantId != null) {
            existing = projectRepository.findByIdAndTenantId(projectId, tenantId).orElse(null);
        } else {
            UUID ownerId = getOwnerId(auth);
            existing = projectRepository.findById(projectId)
                    .filter(p -> p.getOwnerId().equals(ownerId) || hasAdminRole(auth))
                    .orElse(null);
        }

        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        Project restored = projectService.restoreProject(projectId);
        return ResponseEntity.ok(toDto(restored));
    }

    // ─────────────────────────────────────────────────────────────────
    // API Keys
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/keys")
    @Operation(summary = "List API keys for project")
    public ResponseEntity<?> listKeys(@PathVariable UUID projectId, Authentication auth) {
        // Verify tenant membership
        Project project = verifyProjectAccess(projectId, auth).orElse(null);

        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        
        List<ApiKey> keys = apiKeyService.getKeysForProject(projectId);
        return ResponseEntity.ok(keys.stream()
                .map(this::toKeyDto)
                .toList());
    }

    @PostMapping("/{projectId}/keys")
    @Operation(summary = "Create new API key")
    public ResponseEntity<?> createKey(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateKeyRequest request,
            Authentication auth) {

        // Verify tenant membership
        Project project = verifyProjectAccess(projectId, auth).orElse(null);

        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        ApiKeyService.KeyPairResult result = apiKeyService.createKeyPair(
                projectId,
                request.getName(),
                request.getKeyType());

        return ResponseEntity.status(HttpStatus.CREATED).body(new KeyCreatedResponse(
                toKeyDto(result.apiKey()),
                result.secretKeyPlaintext() // Shown only once!
        ));
    }

    @PostMapping("/{projectId}/keys/{keyId}/rotate")
    @Operation(summary = "Rotate API key (old key valid during grace period)")
    public ResponseEntity<?> rotateKey(
            @PathVariable UUID projectId,
            @PathVariable UUID keyId,
            Authentication auth) {

        // Verify tenant membership
        Project project = verifyProjectAccess(projectId, auth).orElse(null);

        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            // ApiKeyService.rotateKey now verifies key belongs to project
            ApiKeyService.KeyPairResult result = apiKeyService.rotateKey(projectId, keyId, auth.getName());

            return ResponseEntity.ok(new KeyCreatedResponse(
                    toKeyDto(result.apiKey()),
                    result.secretKeyPlaintext() // Shown only once!
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "forbidden",
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{projectId}/keys/{keyId}")
    @Operation(summary = "Revoke API key immediately")
    public ResponseEntity<?> revokeKey(
            @PathVariable UUID projectId,
            @PathVariable UUID keyId,
            @RequestParam(required = false) String reason,
            Authentication auth) {

        // Verify tenant membership
        Project project = verifyProjectAccess(projectId, auth).orElse(null);

        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            // ApiKeyService.revokeKey now verifies key belongs to project
            apiKeyService.revokeKey(projectId, keyId, auth.getName(), reason != null ? reason : "Manual revocation");

            return ResponseEntity.ok(Map.of("message", "Key revoked"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "forbidden",
                    "message", e.getMessage()
            ));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────

    /**
     * Verify the authenticated user has access to the project.
     * Uses tenant-scoped lookup when tenant_id is present in JWT,
     * falls back to owner-based check for backward compatibility.
     */
    private java.util.Optional<Project> verifyProjectAccess(UUID projectId, Authentication auth) {
        UUID tenantId = getTenantId(auth);

        if (tenantId != null) {
            // Tenant-scoped access check (Phase 4)
            return projectService.getProjectForTenant(projectId, tenantId);
        }

        // Fallback: owner-based check (backward compatibility)
        UUID ownerId = getOwnerId(auth);
        return projectService.getProject(projectId)
                .filter(p -> p.getOwnerId().equals(ownerId) || hasAdminRole(auth));
    }

    /**
     * Extract user ID from authentication principal.
     * Supports JWT tokens with 'sub' claim.
     */
    private UUID getOwnerId(Authentication auth) {
        if (auth == null) {
            throw new SecurityException("Authentication required");
        }
        
        Object principal = auth.getPrincipal();
        
        // JWT token (from OIDC/OAuth2)
        if (principal instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException e) {
                    // Subject might be username, not UUID
                    log.debug("JWT subject is not a UUID: {}", sub);
                }
            }
        }
        
        // Fallback: try to parse from auth name
        String name = auth.getName();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            // In production, reject invalid tokens; in dev, allow default for testing
            boolean isProd = env.matchesProfiles("prod", "production");
            if (isProd) {
                log.error("Could not extract UUID from auth in production");
                throw new SecurityException("Invalid authentication: unable to determine user identity");
            }
            log.warn("Could not extract UUID from auth, using default (dev only)");
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
    }

    /**
     * Extract tenant ID from JWT claims.
     * Returns null if not present (backward compatibility for dev mode).
     */
    private UUID getTenantId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String tenantIdStr = jwt.getClaimAsString("tenant_id");
            if (tenantIdStr != null) {
                try {
                    return UUID.fromString(tenantIdStr);
                } catch (IllegalArgumentException e) {
                    log.debug("JWT tenant_id is not a valid UUID: {}", tenantIdStr);
                }
            }
        }
        return null;
    }

    /**
     * Check if user has super-admin role.
     */
    private boolean hasAdminRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_arkil:super-admin") ||
                               a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    private ProjectDto toDto(Project project) {
        // Build OIDC config if project has a registered client
        OidcConfig oidcConfig = null;
        if (project.getRegisteredClientId() != null) {
            String clientId = registeredClientBridge.getClientIdForProject(project.getSlug());
            String issuer = getIssuerUrl();
            oidcConfig = new OidcConfig(
                    clientId,
                    issuer,
                    issuer + "/oauth2/authorize",
                    issuer + "/oauth2/token",
                    issuer + "/oauth2/jwks",
                    issuer + "/userinfo"
            );
        }

        return new ProjectDto(
                project.getId(),
                project.getName(),
                project.getSlug(),
                project.getDescription(),
                project.getIconUrl(),
                project.getEnvironment(),
                project.getAllowedOrigins(),
                project.getRedirectUris(),
                project.isActive(),
                project.getCreatedAt().toString(),
                oidcConfig
        );
    }

    private String getIssuerUrl() {
        String port = env.getProperty("server.port", "8080");
        return "http://localhost:" + port;
    }

    private DeletedProjectDto toDeletedDto(Project project) {
        long daysRemaining = 0;
        if (project.getDeletedAt() != null) {
            long daysSinceDelete = java.time.Duration.between(project.getDeletedAt(), java.time.Instant.now()).toDays();
            daysRemaining = Math.max(0, 7 - daysSinceDelete);
        }
        return new DeletedProjectDto(
                project.getId(),
                project.getName(),
                project.getSlug(),
                project.getDeletedAt() != null ? project.getDeletedAt().toString() : null,
                daysRemaining
        );
    }

    private ApiKeyDto toKeyDto(ApiKey key) {
        return new ApiKeyDto(
                key.getId(),
                key.getKeyId(),
                key.getName(),
                key.getPublishableKey(),
                "sk_***" + key.getSecretKeyLast4(), // Masked
                key.getKeyType(),
                key.getEffectiveStatus(java.time.Instant.now()),
                key.getCreatedAt().toString(),
                key.getLastUsedAt() != null ? key.getLastUsedAt().toString() : null,
                key.getGracePeriodEndsAt() != null ? key.getGracePeriodEndsAt().toString() : null
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────────────

    public record OidcConfig(
            String clientId,
            String issuerUrl,
            String authorizationEndpoint,
            String tokenEndpoint,
            String jwksUri,
            String userinfoEndpoint
    ) {}

    public record ProjectDto(
            UUID id,
            String name,
            String slug,
            String description,
            String iconUrl,
            Project.Environment environment,
            List<String> allowedOrigins,
            List<String> redirectUris,
            boolean active,
            String createdAt,
            OidcConfig oidcConfig
    ) {}

    public record DeletedProjectDto(
            UUID id,
            String name,
            String slug,
            String deletedAt,
            long daysRemaining
    ) {}

    public record ApiKeyDto(
            UUID id,
            String keyId,
            String name,
            String publishableKey,
            String secretKeyMasked,
            ApiKey.KeyType keyType,
            ApiKey.KeyStatus status,
            String createdAt,
            String lastUsedAt,
            String gracePeriodEndsAt
    ) {}

    public record ProjectCreatedResponse(
            ProjectDto project,
            ApiKeyDto apiKey,
            String secretKey // Shown only once!
    ) {}

    public record KeyCreatedResponse(
            ApiKeyDto apiKey,
            String secretKey // Shown only once!
    ) {}

    @Data
    public static class CreateProjectRequest {
        @NotBlank(message = "Project name is required")
        private String name;
        private String slug;
        private String description;
        private Project.Environment environment;
        private List<String> allowedOrigins;
        private List<String> redirectUris;
    }

    @Data
    public static class UpdateProjectRequest {
        private String name;
        private String description;
        private String iconUrl;
        private Project.Environment environment;
        private List<String> allowedOrigins;
        private List<String> redirectUris;
    }

    @Data
    public static class CreateKeyRequest {
        @NotBlank(message = "Key name is required")
        private String name;
        private ApiKey.KeyType keyType = ApiKey.KeyType.TEST;
    }
}
