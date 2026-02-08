package com.arkil.credential.password;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordCredentialRepository extends JpaRepository<PasswordCredential, UUID> {

    Optional<PasswordCredential> findByUser_Id(UUID userId);

    boolean existsByUser_Id(UUID userId);
}
