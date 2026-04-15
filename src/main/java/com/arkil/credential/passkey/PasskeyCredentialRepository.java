package com.arkil.credential.passkey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, UUID> {

    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    List<PasskeyCredential> findByUserId(UUID userId);

    List<PasskeyCredential> findByUserIdAndRpId(UUID userId, String rpId);

    boolean existsByCredentialId(String credentialId);

    @Modifying
    @Transactional
    void deleteByUserIdAndCredentialId(UUID userId, String credentialId);
}
