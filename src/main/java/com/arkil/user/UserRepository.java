package com.arkil.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<ArkilUser, UUID> {

    Optional<ArkilUser> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<ArkilUser> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    /**
     * Find user by email (global lookup for password reset, etc.)
     */
    Optional<ArkilUser> findByEmail(String email);

    /**
     * Find user by username (global lookup for login).
     */
    Optional<ArkilUser> findByUsername(String username);

    /**
     * Check if email is already in use (global, for developer registration).
     */
    boolean existsByEmail(String email);
}
