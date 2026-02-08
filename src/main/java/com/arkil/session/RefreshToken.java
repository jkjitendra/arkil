package com.arkil.session;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token entity for tracking token families.
 * Enables rotation and reuse detection.
 *
 * Token Family: All tokens created from the same original login form a family.
 * When a refresh token is used, a new one is issued and the old one is invalidated.
 * If a previously-used token is presented again (reuse), the entire family is revoked.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_token", columnList = "tokenHash"),
        @Index(name = "idx_refresh_token_family", columnList = "familyId"),
        @Index(name = "idx_refresh_token_user", columnList = "userId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Hash of the actual token (never store plaintext tokens).
     */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * Token family ID. All tokens from the same login session share this ID.
     * Used for reuse detection and bulk revocation.
     */
    @Column(nullable = false)
    private UUID familyId;

    /**
     * User this token belongs to.
     */
    @Column(nullable = false)
    private UUID userId;

    /**
     * Client/project this token was issued for.
     */
    @Column(nullable = false)
    private String clientId;

    /**
     * When this token expires.
     */
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * When this token was created.
     */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * When this token was used (null if never used).
     */
    private Instant usedAt;

    /**
     * When this token was revoked (null if active).
     */
    private Instant revokedAt;

    /**
     * Reason for revocation.
     */
    @Enumerated(EnumType.STRING)
    private RevocationReason revocationReason;

    /**
     * Device/session info for display.
     */
    private String userAgent;

    /**
     * IP address where token was created.
     */
    private String ipAddress;

    public enum RevocationReason {
        LOGOUT,           // User logged out
        ROTATION,         // Token was rotated (normal use)
        REUSE_DETECTED,   // Token reuse detected (security event)
        PASSWORD_CHANGE,  // User changed password
        ADMIN_ACTION,     // Admin revoked token
        EXPIRED           // Token expired
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public void revoke(RevocationReason reason) {
        this.revokedAt = Instant.now();
        this.revocationReason = reason;
    }
}
