package com.arkil.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailTokenRepository extends JpaRepository<EmailToken, UUID> {

    Optional<EmailToken> findByToken(String token);

    Optional<EmailToken> findByTokenAndType(String token, EmailToken.TokenType type);

    @Query("SELECT t FROM EmailToken t WHERE t.userId = :userId AND t.type = :type AND t.usedAt IS NULL AND t.expiresAt > :now ORDER BY t.createdAt DESC")
    Optional<EmailToken> findValidTokenByUserAndType(UUID userId, EmailToken.TokenType type, Instant now);

    @Modifying
    @Query("DELETE FROM EmailToken t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(Instant now);

    @Modifying
    @Query("UPDATE EmailToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.type = :type AND t.usedAt IS NULL")
    int invalidateTokensByUserAndType(UUID userId, EmailToken.TokenType type, Instant now);
}
