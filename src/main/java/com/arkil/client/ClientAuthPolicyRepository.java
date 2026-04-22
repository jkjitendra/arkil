package com.arkil.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientAuthPolicyRepository extends JpaRepository<ClientAuthPolicy, UUID> {

    Optional<ClientAuthPolicy> findByClientId(String clientId);

    Optional<ClientAuthPolicy> findByRegisteredClientInternalId(String registeredClientInternalId);

    boolean existsByClientId(String clientId);

    void deleteByRegisteredClientInternalId(String registeredClientInternalId);

    void deleteByClientId(String clientId);
}
