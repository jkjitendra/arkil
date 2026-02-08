package com.arkil.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Find all active tokens in a family (for reuse detection).
     */
    @Query("SELECT t FROM RefreshToken t WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    List<RefreshToken> findActiveByFamilyId(UUID familyId);

    /**
     * Find all tokens for a user (for listing sessions).
     */
    @Query("SELECT t FROM RefreshToken t WHERE t.userId = :userId AND t.revokedAt IS NULL AND t.expiresAt > :now ORDER BY t.createdAt DESC")
    List<RefreshToken> findActiveByUserId(UUID userId, Instant now);

    /**
     * Revoke all tokens in a family (for reuse detection or logout-all).
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revocationReason = :reason WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeByFamilyId(UUID familyId, Instant now, RefreshToken.RevocationReason reason);

    /**
     * Revoke all tokens for a user (password change, security event).
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revocationReason = :reason WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeByUserId(UUID userId, Instant now, RefreshToken.RevocationReason reason);

    /**
     * Cleanup expired tokens.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(Instant now);

    /**
     * Count active sessions for a user.
     */
    @Query("SELECT COUNT(t) FROM RefreshToken t WHERE t.userId = :userId AND t.revokedAt IS NULL AND t.expiresAt > :now")
    long countActiveByUserId(UUID userId, Instant now);
}
