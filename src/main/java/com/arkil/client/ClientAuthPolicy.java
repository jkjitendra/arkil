package com.arkil.client;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client authentication policy - determines which auth methods are enabled for a client.
 * This is the core of the "Client Choice" philosophy.
 */
@Entity
@Table(name = "client_auth_policies", indexes = {
        @Index(name = "idx_policy_client_id", columnList = "clientId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientAuthPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Internal registered client ID (FK to Spring's RegisteredClient).
     */
    @Column(nullable = false, unique = true)
    private String registeredClientInternalId;

    /**
     * Public OAuth2 client_id (denormalized for fast lookup).
     */
    @Column(nullable = false, unique = true)
    private String clientId;

    /**
     * Enabled authentication modules for this client.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "client_enabled_modules", joinColumns = @JoinColumn(name = "policy_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "module")
    @Builder.Default
    private Set<AuthModule> enabledModules = new HashSet<>();

    /**
     * Per-module configuration (JSON).
     * E.g., {"OAUTH2_GOOGLE": {"scopes": ["email", "profile"]}, "PASSKEY": {"rpId": "example.com"}}
     */
    @Column(columnDefinition = "TEXT")
    private String moduleConfig;

    /**
     * MFA policy configuration (JSON).
     * E.g., {"required": false, "factors": ["TOTP"], "stepUpTriggers": ["password_change"]}
     */
    @Column(columnDefinition = "TEXT")
    private String mfaPolicy;

    /**
     * Theme/branding configuration (JSON).
     * E.g., {"logo": "https://...", "primaryColor": "#e94560"}
     */
    @Column(columnDefinition = "TEXT")
    private String themeConfig;

    /**
     * Version for optimistic locking (ETag support).
     */
    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    private String updatedBy;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
