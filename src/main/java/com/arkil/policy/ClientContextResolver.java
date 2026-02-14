package com.arkil.policy;

import com.arkil.client.ClientAuthPolicy;
import com.arkil.client.ClientAuthPolicyRepository;
import com.arkil.project.ApiKey;
import com.arkil.project.ApiKeyRepository;
import com.arkil.project.Project;
import com.arkil.project.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the client context from the request.
 * Resolution order:
 * 1. Query parameter: ?client_id=...
 * 2. Publishable key: ?pk=... or X-Publishable-Key header
 * 3. Session attribute (stored after first resolution)
 * 4. Host mapping (future: domain-to-client mapping)
 *
 * When resolved via pk_, the publishable key is used to look up the project,
 * then the project's registered client ID is used to find the auth policy.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientContextResolver {

    private static final String CLIENT_ID_PARAM = "client_id";
    private static final String PK_PARAM = "pk";
    private static final String PK_HEADER = "X-Publishable-Key";
    private static final String SESSION_CLIENT_ID = "arkil.client_id";

    private final ClientAuthPolicyRepository policyRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ProjectRepository projectRepository;

    /**
     * Resolve client context from the request.
     */
    public ClientContext resolve(HttpServletRequest request) {
        // 1. Try client_id query parameter (direct OAuth2 client ID)
        String clientId = request.getParameter(CLIENT_ID_PARAM);

        // 2. Try publishable key: ?pk= or X-Publishable-Key header
        if (clientId == null) {
            String pk = request.getParameter(PK_PARAM);
            if (pk == null) {
                pk = request.getHeader(PK_HEADER);
            }
            if (pk != null && !pk.isBlank()) {
                return resolveFromPublishableKey(pk, request);
            }
        }

        // 3. Try session (for multi-step flows like OAuth)
        if (clientId == null && request.getSession(false) != null) {
            clientId = (String) request.getSession().getAttribute(SESSION_CLIENT_ID);
        }

        // 4. Future: Try host mapping

        if (clientId == null || clientId.isBlank()) {
            return ClientContext.unresolved("Missing client_id parameter");
        }

        return resolveFromClientId(clientId, request);
    }

    /**
     * Resolve context from a direct OAuth2 client_id (e.g., proj_my-app).
     */
    private ClientContext resolveFromClientId(String clientId, HttpServletRequest request) {
        storeInSession(request, clientId);

        Optional<ClientAuthPolicy> policyOpt = policyRepository.findByClientId(clientId);
        if (policyOpt.isEmpty()) {
            log.warn("No policy found for client_id: {}", clientId);
            return ClientContext.unresolved("Unknown client: " + clientId);
        }

        log.debug("Resolved client context via client_id: {} with modules {}", clientId, policyOpt.get().getEnabledModules());
        return ClientContext.resolved(clientId, policyOpt.get());
    }

    /**
     * Resolve context from a publishable key (pk_test_xxx or pk_live_xxx).
     * Looks up the API key -> project -> registered client -> auth policy.
     */
    private ClientContext resolveFromPublishableKey(String pk, HttpServletRequest request) {
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByPublishableKeyWithActiveProject(pk);
        if (apiKeyOpt.isEmpty()) {
            log.warn("No active API key found for publishable key: {}...", pk.substring(0, Math.min(pk.length(), 12)));
            return ClientContext.unresolved("Invalid or inactive publishable key");
        }

        ApiKey apiKey = apiKeyOpt.get();
        Optional<Project> projectOpt = projectRepository.findById(apiKey.getProjectId());
        if (projectOpt.isEmpty() || projectOpt.get().getRegisteredClientId() == null) {
            log.warn("Project not found or has no registered client for pk: {}", apiKey.getProjectId());
            return ClientContext.unresolved("Project not configured for authentication");
        }

        Project project = projectOpt.get();
        String clientId = "proj_" + project.getSlug();

        storeInSession(request, clientId);

        Optional<ClientAuthPolicy> policyOpt = policyRepository.findByClientId(clientId);
        if (policyOpt.isEmpty()) {
            log.warn("No policy found for project client_id: {}", clientId);
            return ClientContext.unresolved("No auth policy configured for project");
        }

        log.debug("Resolved client context via pk_ key: {} -> client_id: {} with modules {}",
                pk.substring(0, Math.min(pk.length(), 12)), clientId, policyOpt.get().getEnabledModules());
        return ClientContext.resolved(clientId, policyOpt.get());
    }

    private void storeInSession(HttpServletRequest request, String clientId) {
        if (request.getSession(false) != null || request.getSession() != null) {
            request.getSession().setAttribute(SESSION_CLIENT_ID, clientId);
        }
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
