package com.arkil.project;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Project entity - represents a developer's application/project.
 * Each project has its own auth policies, API keys, and user pool.
 */
@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_project_slug", columnList = "slug"),
        @Index(name = "idx_project_owner", columnList = "ownerId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Human-readable project name.
     */
    @Column(nullable = false)
    private String name;

    /**
     * URL-safe slug for project identification.
     */
    @Column(nullable = false, unique = true)
    private String slug;

    /**
     * Project owner (admin user).
     */
    @Column(nullable = false)
    private UUID ownerId;

    /**
     * Environment: development, staging, production.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Environment environment = Environment.DEVELOPMENT;

    /**
     * CORS allowed origins for this project.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_allowed_origins", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "origin")
    @Builder.Default
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * Allowed redirect URIs for OAuth flows.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_redirect_uris", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "redirect_uri")
    @Builder.Default
    private List<String> redirectUris = new ArrayList<>();

    /**
     * Project description (optional).
     */
    @Column(length = 500)
    private String description;

    /**
     * Project icon URL (optional).
     */
    private String iconUrl;

    /**
     * Whether the project is active.
     */
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    /**
     * When the project was soft-deleted. Null means active.
     * Projects with deletedAt older than 7 days can be permanently removed.
     */
    private Instant deletedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum Environment {
        DEVELOPMENT,
        STAGING,
        PRODUCTION
    }
}
