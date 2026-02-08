package com.arkil.project;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for project management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ApiKeyService apiKeyService;

    /**
     * Create a new project with initial API keys.
     */
    @Transactional
    public ProjectWithKeys createProject(CreateProjectRequest request, UUID ownerId) {
        // Check name uniqueness for this owner
        if (projectRepository.existsByNameAndOwnerIdAndDeletedAtIsNull(request.name(), ownerId)) {
            throw new IllegalArgumentException("Project name already exists: " + request.name());
        }

        // Generate slug if not provided
        String slug = request.slug() != null ? request.slug() : generateSlug(request.name());

        // Ensure slug is unique (among non-deleted projects)
        if (projectRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            throw new IllegalArgumentException("Project slug already exists: " + slug);
        }

        Project project = Project.builder()
                .name(request.name())
                .slug(slug)
                .ownerId(ownerId)
                .description(request.description())
                .environment(request.environment() != null ? request.environment() : Project.Environment.DEVELOPMENT)
                .allowedOrigins(request.allowedOrigins() != null ? request.allowedOrigins() : List.of())
                .redirectUris(request.redirectUris() != null ? request.redirectUris() : List.of())
                .build();

        projectRepository.save(project);

        // Create initial API key pair
        ApiKeyService.KeyPairResult testKey = apiKeyService.createKeyPair(
                project.getId(), "Default Test Key", ApiKey.KeyType.TEST);

        log.info("Created project {} with slug {}", project.getId(), slug);

        return new ProjectWithKeys(project, testKey.apiKey(), testKey.secretKeyPlaintext());
    }

    /**
     * Get project by ID.
     */
    public Optional<Project> getProject(UUID projectId) {
        return projectRepository.findById(projectId);
    }

    /**
     * Get project by slug.
     */
    public Optional<Project> getProjectBySlug(String slug) {
        return projectRepository.findBySlug(slug);
    }

    /**
     * List projects for an owner.
     */
    public List<Project> listProjects(UUID ownerId) {
        return projectRepository.findByOwnerIdAndDeletedAtIsNull(ownerId);
    }

    /**
     * Update project.
     */
    @Transactional
    public Project updateProject(UUID projectId, UpdateProjectRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (request.name() != null) {
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        if (request.iconUrl() != null) {
            project.setIconUrl(request.iconUrl());
        }
        if (request.allowedOrigins() != null) {
            project.setAllowedOrigins(request.allowedOrigins());
        }
        if (request.redirectUris() != null) {
            project.setRedirectUris(request.redirectUris());
        }
        if (request.environment() != null) {
            project.setEnvironment(request.environment());
        }

        return projectRepository.save(project);
    }

    /**
     * Soft-delete project.
     */
    @Transactional
    public void deleteProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // Mark as deleted (soft-delete)
        project.setActive(false);
        project.setDeletedAt(Instant.now());
        projectRepository.save(project);

        log.info("Soft-deleted project {} - will be permanently removed after 7 days", projectId);
    }

    /**
     * Restore a soft-deleted project (within retention period).
     */
    @Transactional
    public Project restoreProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (project.getDeletedAt() == null) {
            throw new IllegalStateException("Project is not deleted");
        }

        // Check if within retention period (7 days)
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        if (project.getDeletedAt().isBefore(cutoff)) {
            throw new IllegalStateException("Project cannot be restored - retention period expired");
        }

        project.setActive(true);
        project.setDeletedAt(null);
        projectRepository.save(project);

        log.info("Restored project {}", projectId);
        return project;
    }

    /**
     * List deleted projects for an owner (for trash view).
     */
    public List<Project> listDeletedProjects(UUID ownerId) {
        return projectRepository.findByOwnerIdAndDeletedAtIsNotNull(ownerId);
    }

    // ─────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ─────────────────────────────────────────────────────────────────
    // Request DTOs
    // ─────────────────────────────────────────────────────────────────

    public record CreateProjectRequest(
            String name,
            String slug,
            String description,
            Project.Environment environment,
            List<String> allowedOrigins,
            List<String> redirectUris
    ) {}

    public record UpdateProjectRequest(
            String name,
            String description,
            String iconUrl,
            Project.Environment environment,
            List<String> allowedOrigins,
            List<String> redirectUris
    ) {}

    public record ProjectWithKeys(
            Project project,
            ApiKey apiKey,
            String secretKeyPlaintext
    ) {}
}
