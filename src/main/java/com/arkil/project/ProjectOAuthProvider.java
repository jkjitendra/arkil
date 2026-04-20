package com.arkil.project;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores a developer's OAuth2 provider credentials for a specific project.
 * When an end-user clicks "Sign in with Google" on a project's hosted login page,
 * these credentials are used to initiate the OAuth2 flow with the developer's own
 * Google/GitHub/etc. application.
 */
@Entity
@Table(name = "project_oauth_providers", indexes = {
        @Index(name = "idx_oauth_provider_project", columnList = "project_id"),
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_provider_env", columnNames = {"project_id", "provider", "environment"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectOAuthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /**
     * Provider identifier: "google", "github", "apple", "linkedin", "custom-oidc".
     */
    @Column(nullable = false, length = 50)
    private String provider;

    /**
     * Optional UI label for the provider button, primarily used for custom OIDC.
     */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /**
     * The developer's OAuth app client ID (e.g., Google Console client ID).
     */
    @Column(name = "client_id", nullable = false)
    private String clientId;

    /**
     * The developer's OAuth app client secret (encrypted at rest).
     */
    @Column(name = "client_secret_encrypted", nullable = false)
    private String clientSecretEncrypted;

    /**
     * OAuth scopes (comma-separated). Defaults provided per provider.
     */
    @Column(length = 500)
    @Builder.Default
    private String scopes = "";

    @Column(name = "issuer_uri", length = 1000)
    private String issuerUri;

    @Column(name = "authorization_uri", length = 1000)
    private String authorizationUri;

    @Column(name = "token_uri", length = 1000)
    private String tokenUri;

    @Column(name = "user_info_uri", length = 1000)
    private String userInfoUri;

    @Column(name = "jwk_set_uri", length = 1000)
    private String jwkSetUri;

    @Column(name = "user_name_attribute", length = 100)
    private String userNameAttribute;

    /**
     * Environment this config applies to.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Project.Environment environment = Project.Environment.DEVELOPMENT;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
