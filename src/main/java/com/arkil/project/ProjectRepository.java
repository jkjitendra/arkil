package com.arkil.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findBySlug(String slug);

    List<Project> findByOwnerId(UUID ownerId);

    List<Project> findByOwnerIdAndActiveTrue(UUID ownerId);

    /**
     * Find active (non-deleted) projects for owner.
     */
    List<Project> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    /**
     * Find deleted projects for owner (for trash/restore view).
     */
    List<Project> findByOwnerIdAndDeletedAtIsNotNull(UUID ownerId);

    /**
     * Find projects deleted before a certain time (for cleanup job).
     */
    List<Project> findByDeletedAtBefore(Instant cutoff);

    boolean existsBySlug(String slug);

    /**
     * Check if a project name already exists for an owner (excludes deleted).
     */
    boolean existsByNameAndOwnerIdAndDeletedAtIsNull(String name, UUID ownerId);

    /**
     * Check if slug exists (excludes deleted projects).
     */
    boolean existsBySlugAndDeletedAtIsNull(String slug);

    List<Project> findByEnvironment(Project.Environment environment);

    /**
     * Find active projects by tenant (for multi-tenancy scoping).
     */
    List<Project> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

    /**
     * Find a specific project scoped to a tenant.
     */
    Optional<Project> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Find deleted projects by tenant (for tenant-scoped trash view).
     */
    List<Project> findByTenantIdAndDeletedAtIsNotNull(UUID tenantId);

    /**
     * Check if a project name already exists within a tenant (excludes deleted).
     */
    boolean existsByNameAndTenantIdAndDeletedAtIsNull(String name, UUID tenantId);

    /**
     * Find project by its registered client ID.
     */
    Optional<Project> findByRegisteredClientId(String registeredClientId);
}

