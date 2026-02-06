package com.arkil.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * Service for managing client authentication policies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientPolicyService {

    private final ClientAuthPolicyRepository policyRepository;

    /**
     * Get policy by client ID.
     */
    @Transactional(readOnly = true)
    public Optional<ClientAuthPolicy> getPolicyByClientId(String clientId) {
        return policyRepository.findByClientId(clientId);
    }

    /**
     * Create or update a client's authentication policy.
     */
    @Transactional
    public ClientAuthPolicy savePolicy(ClientAuthPolicy policy, String updatedBy) {
        policy.setUpdatedBy(updatedBy);
        ClientAuthPolicy saved = policyRepository.save(policy);
        log.info("Saved policy for client {}: modules={}", saved.getClientId(), saved.getEnabledModules());
        return saved;
    }

    /**
     * Update enabled modules for a client.
     */
    @Transactional
    public ClientAuthPolicy updateEnabledModules(String clientId, Set<AuthModule> modules, String updatedBy) {
        ClientAuthPolicy policy = policyRepository.findByClientId(clientId)
                .orElseThrow(() -> new PolicyNotFoundException(clientId));

        policy.setEnabledModules(modules);
        policy.setUpdatedBy(updatedBy);
        return policyRepository.save(policy);
    }

    /**
     * Check if a specific module is enabled for a client.
     */
    @Transactional(readOnly = true)
    public boolean isModuleEnabled(String clientId, AuthModule module) {
        return policyRepository.findByClientId(clientId)
                .map(policy -> policy.getEnabledModules().contains(module))
                .orElse(false); // Deny by default
    }

    /**
     * Create a new policy for a client (deny-all by default).
     */
    @Transactional
    public ClientAuthPolicy createPolicy(String registeredClientInternalId, String clientId, String createdBy) {
        if (policyRepository.existsByClientId(clientId)) {
            throw new PolicyAlreadyExistsException(clientId);
        }

        ClientAuthPolicy policy = ClientAuthPolicy.builder()
                .registeredClientInternalId(registeredClientInternalId)
                .clientId(clientId)
                .updatedBy(createdBy)
                .build();

        return policyRepository.save(policy);
    }
}
