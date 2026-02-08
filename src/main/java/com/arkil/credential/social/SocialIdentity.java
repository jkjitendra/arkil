package com.arkil.credential.social;

import com.arkil.user.ArkilUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Social identity - links an external OAuth2 identity to an ArkilUser.
 * One user can have multiple social identities (Google, GitHub, etc.)
 */
@Entity
@Table(name = "social_identities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_social_provider_subject", columnNames = {"provider", "providerSubjectId"})
}, indexes = {
        @Index(name = "idx_social_provider_subject", columnList = "provider, providerSubjectId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private ArkilUser user;

    /**
     * OAuth2 provider name (google, github, apple, linkedin).
     */
    @Column(nullable = false)
    private String provider;

    /**
     * The subject ID from the OAuth2 provider (unique per provider).
     */
    @Column(nullable = false)
    private String providerSubjectId;

    /**
     * Email from the OAuth2 provider (may differ from ArkilUser.email).
     */
    private String providerEmail;

    /**
     * Display name from the OAuth2 provider.
     */
    private String providerDisplayName;

    /**
     * Profile picture URL from the provider.
     */
    private String pictureUrl;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant lastUsedAt;

    @PreUpdate
    protected void onUpdate() {
        this.lastUsedAt = Instant.now();
    }
}
