package com.arkil.project;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * API Key entity for project authentication.
 * 
 * - pk_ (publishable key): Identifier only, stored plaintext
 * - sk_ (secret key): Stored hashed, shown only once on creation
 */
@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_apikey_project", columnList = "project_id"),
        @Index(name = "idx_apikey_key_id", columnList = "key_id"),
        @Index(name = "idx_apikey_pk", columnList = "publishable_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Human-readable key identifier (e.g., "Production Key", "Dev Key").
     */
    @Column(name = "name")
    private String name;

    /**
     * Short key ID for display and lookup (e.g., "key_abc123").
     */
    @Column(name = "key_id", nullable = false, unique = true)
    private String keyId;

    /**
     * Project this key belongs to.
     */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /**
     * Publishable key (pk_live_xxx or pk_test_xxx).
     * Safe to expose in client-side code.
     */
    @Column(name = "publishable_key", nullable = false, unique = true)
    private String publishableKey;

    /**
     * Secret key hash (BCrypt/Argon2).
     * The actual sk_ is shown only once on creation.
     */
    @Column(name = "secret_key_hash", nullable = false)
    private String secretKeyHash;

    /**
     * Last 4 characters of secret key (for display: sk_***abc1).
     */
    @Column(name = "secret_key_last4", nullable = false, length = 4)
    private String secretKeyLast4;

    /**
     * Key type: live or test.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false)
    @Builder.Default
    private KeyType keyType = KeyType.TEST;

    /**
     * Key status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private KeyStatus status = KeyStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * When the key was last used for authentication.
     */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /**
     * When the key was revoked (if applicable).
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Who revoked the key.
     */
    @Column(name = "revoked_by")
    private String revokedBy;

    /**
     * Reason for revocation.
     */
    @Column(name = "revocation_reason")
    private String revocationReason;

    /**
     * Grace period end for rotated keys.
     * Old key still valid until this time.
     */
    @Column(name = "grace_period_ends_at")
    private Instant gracePeriodEndsAt;

    public enum KeyType {
        LIVE,
        TEST
    }

    public enum KeyStatus {
        ACTIVE,
        ROTATING,    // Being replaced, still valid during grace period
        REVOKED,
        EXPIRED
    }

    /**
     * Simple check if status is ACTIVE (deterministic, no time-based logic).
     */
    public boolean isActive() {
        return status == KeyStatus.ACTIVE;
    }

    /**
     * Get effective status for UI display.
     * Derives EXPIRED for ROTATING keys past grace period.
     */
    public KeyStatus getEffectiveStatus(Instant now) {
        if (status == KeyStatus.ROTATING) {
            if (gracePeriodEndsAt == null || !gracePeriodEndsAt.isAfter(now)) {
                return KeyStatus.EXPIRED;
            }
        }
        return status;
    }

    /**
     * Check if key is usable for authentication at the given instant.
     * A key is usable if:
     * - status == ACTIVE, OR
     * - status == ROTATING and within grace period
     */
    public boolean isUsableForAuth(Instant now) {
        return switch (status) {
            case ACTIVE -> true;
            case ROTATING -> gracePeriodEndsAt != null && gracePeriodEndsAt.isAfter(now);
            default -> false; // REVOKED, EXPIRED
        };
    }

    /**
     * Mark key as used. Returns true if updated (for write optimization).
     * Only updates if lastUsedAt is more than 1 minute old to reduce writes.
     */
    public boolean markUsedIfStale() {
        Instant now = Instant.now();
        if (lastUsedAt == null || lastUsedAt.plusSeconds(60).isBefore(now)) {
            this.lastUsedAt = now;
            return true;
        }
        return false;
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }

    public void revoke(String revokedBy, String reason) {
        this.status = KeyStatus.REVOKED;
        this.revokedAt = Instant.now();
        this.revokedBy = revokedBy;
        this.revocationReason = reason;
    }
}
