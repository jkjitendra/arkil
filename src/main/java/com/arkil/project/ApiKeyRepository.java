package com.arkil.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyId(String keyId);

    Optional<ApiKey> findByPublishableKey(String publishableKey);

    /**
     * Find all active keys for a project.
     */
    @Query("SELECT k FROM ApiKey k WHERE k.projectId = :projectId AND k.status IN ('ACTIVE', 'ROTATING')")
    List<ApiKey> findActiveByProjectId(UUID projectId);

    /**
     * Find all keys for a project (including revoked).
     */
    List<ApiKey> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    /**
     * Find by key type (live/test) for a project.
     */
    List<ApiKey> findByProjectIdAndKeyType(UUID projectId, ApiKey.KeyType keyType);

    /**
     * Count active keys for a project.
     */
    @Query("SELECT COUNT(k) FROM ApiKey k WHERE k.projectId = :projectId AND k.status = 'ACTIVE'")
    long countActiveByProjectId(UUID projectId);

    /**
     * Expire rotating keys past grace period.
     */
    @Modifying
    @Query("UPDATE ApiKey k SET k.status = 'EXPIRED' WHERE k.status = 'ROTATING' AND k.gracePeriodEndsAt < :now")
    int expireRotatingKeys(Instant now);

    /**
     * Find key by keyId only if the project is not deleted.
     * Single query for auth validation (avoids extra DB hit).
     * Also filters for unusable keys (short-circuit).
     */
    @Query("SELECT k FROM ApiKey k JOIN Project p ON k.projectId = p.id " +
           "WHERE k.keyId = :keyId AND p.deletedAt IS NULL " +
           "AND k.status IN ('ACTIVE', 'ROTATING')")
    Optional<ApiKey> findByKeyIdWithActiveProject(String keyId);

    /**
     * Find key by publishableKey only if the project is not deleted.
     * Enforces auth security for client-side keys.
     */
    @Query("SELECT k FROM ApiKey k JOIN Project p ON k.projectId = p.id " +
           "WHERE k.publishableKey = :pk AND p.deletedAt IS NULL " +
           "AND k.status IN ('ACTIVE', 'ROTATING')")
    Optional<ApiKey> findByPublishableKeyWithActiveProject(String pk);
}
