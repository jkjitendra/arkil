package com.arkil.tenant;

import com.arkil.policy.ClientContextHolder;
import com.arkil.project.Project;
import com.arkil.project.ProjectRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter that resolves and sets the tenant context for the current request.
 * Runs after authentication so JWT claims are available.
 *
 * Resolution strategy:
 * 1. JWT tenant_id claim (dashboard API calls from authenticated developers)
 * 2. Client context → project → tenantId (end-user auth flows)
 *
 * The resolved tenant ID is stored in TenantContext (ThreadLocal) and cleared
 * after the request completes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    private final ClientContextHolder clientContextHolder;
    private final ProjectRepository projectRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            UUID tenantId = resolveTenantId(request);
            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);
                log.debug("Tenant context set: {}", tenantId);
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolveTenantId(HttpServletRequest request) {
        // 1. Try JWT tenant_id claim (authenticated API calls)
        UUID fromJwt = resolveFromJwt();
        if (fromJwt != null) {
            return fromJwt;
        }

        // 2. Try client context → project → tenantId (end-user auth flows)
        UUID fromClientContext = resolveFromClientContext();
        if (fromClientContext != null) {
            return fromClientContext;
        }

        return null;
    }

    private UUID resolveFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        String tenantIdStr = jwt.getClaimAsString("tenant_id");
        if (tenantIdStr == null) {
            return null;
        }

        try {
            return UUID.fromString(tenantIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant_id in JWT: {}", tenantIdStr);
            return null;
        }
    }

    private UUID resolveFromClientContext() {
        if (!clientContextHolder.hasContext()) {
            return null;
        }

        String clientId = clientContextHolder.getContext().getClientId();
        if (clientId == null || !clientId.startsWith("proj_")) {
            return null;
        }

        // Extract slug from client_id (proj_<slug>)
        String slug = clientId.substring("proj_".length());
        Optional<Project> projectOpt = projectRepository.findBySlug(slug);

        return projectOpt.map(Project::getTenantId).orElse(null);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip for static resources and health checks
        return path.startsWith("/css/") ||
                path.startsWith("/js/") ||
                path.startsWith("/images/") ||
                path.startsWith("/actuator/") ||
                path.startsWith("/h2-console/") ||
                path.startsWith("/.well-known/") ||
                path.equals("/favicon.ico");
    }
}
