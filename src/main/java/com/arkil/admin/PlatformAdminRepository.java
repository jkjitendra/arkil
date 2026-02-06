package com.arkil.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {

    Optional<PlatformAdmin> findByUsername(String username);

    Optional<PlatformAdmin> findByEmail(String email);

    boolean existsByUsername(String username);
}
