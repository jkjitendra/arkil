package com.arkil.policy;

import com.arkil.client.AuthModule;
import com.arkil.client.ClientAuthPolicy;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * Holds the resolved client context for the current request.
 * This is populated by ClientContextResolver and used throughout the request lifecycle.
 */
@Data
@Builder
public class ClientContext {

    /**
     * The OAuth2 client_id for this request.
     */
    private final String clientId;

    /**
     * The authentication policy for this client.
     */
    private final ClientAuthPolicy policy;

    /**
     * Whether a valid client context was resolved.
     */
    private final boolean resolved;

    /**
     * Error message if resolution failed.
     */
    private final String errorMessage;

    /**
     * Check if a specific auth module is enabled.
     */
    public boolean isModuleEnabled(AuthModule module) {
        if (!resolved || policy == null) {
            return false; // Deny by default
        }
        return policy.getEnabledModules().contains(module);
    }

    /**
     * Get enabled modules (empty set if not resolved).
     */
    public Set<AuthModule> getEnabledModules() {
        if (!resolved || policy == null) {
            return Set.of();
        }
        return policy.getEnabledModules();
    }

    /**
     * Create an unresolved context with an error message.
     */
    public static ClientContext unresolved(String errorMessage) {
        return ClientContext.builder()
                .resolved(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * Create a resolved context.
     */
    public static ClientContext resolved(String clientId, ClientAuthPolicy policy) {
        return ClientContext.builder()
                .clientId(clientId)
                .policy(policy)
                .resolved(true)
                .build();
    }
}
