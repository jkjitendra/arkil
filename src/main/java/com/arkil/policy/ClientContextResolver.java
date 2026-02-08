package com.arkil.policy;

import com.arkil.client.ClientAuthPolicy;
import com.arkil.client.ClientAuthPolicyRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the client context from the request.
 * Resolution order:
 * 1. Query parameter: ?client_id=...
 * 2. Session attribute (stored after first resolution)
 * 3. Host mapping (future: domain-to-client mapping)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientContextResolver {

    private static final String CLIENT_ID_PARAM = "client_id";
    private static final String SESSION_CLIENT_ID = "arkil.client_id";

    private final ClientAuthPolicyRepository policyRepository;

    /**
     * Resolve client context from the request.
     */
    public ClientContext resolve(HttpServletRequest request) {
        // 1. Try query parameter
        String clientId = request.getParameter(CLIENT_ID_PARAM);

        // 2. Try session (for multi-step flows like OAuth)
        if (clientId == null && request.getSession(false) != null) {
            clientId = (String) request.getSession().getAttribute(SESSION_CLIENT_ID);
        }

        // 3. Future: Try host mapping
        // String host = request.getServerName();
        // clientId = hostMappingService.getClientIdForHost(host);

        if (clientId == null || clientId.isBlank()) {
            return ClientContext.unresolved("Missing client_id parameter");
        }

        // Store in session for multi-step flows
        if (request.getSession(false) != null || request.getSession() != null) {
            request.getSession().setAttribute(SESSION_CLIENT_ID, clientId);
        }

        // Lookup policy
        Optional<ClientAuthPolicy> policyOpt = policyRepository.findByClientId(clientId);
        if (policyOpt.isEmpty()) {
            log.warn("No policy found for client_id: {}", clientId);
            return ClientContext.unresolved("Unknown client: " + clientId);
        }

        log.debug("Resolved client context: {} with modules {}", clientId, policyOpt.get().getEnabledModules());
        return ClientContext.resolved(clientId, policyOpt.get());
    }

    /**
     * Clear client context from session (e.g., after logout).
     */
    public void clearSession(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession().removeAttribute(SESSION_CLIENT_ID);
        }
    }
}
