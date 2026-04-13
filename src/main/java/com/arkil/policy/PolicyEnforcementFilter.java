package com.arkil.policy;

import com.arkil.client.AuthModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Endpoint guard that blocks disabled authentication routes.
 * This is the "Layered Enforcement" - UI hiding is not enough.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyEnforcementFilter extends OncePerRequestFilter {

    private final ClientContextHolder contextHolder;
    private final ClientContextResolver clientContextResolver;

    // Map of URL patterns to required auth modules
    private static final Map<Pattern, AuthModule> GUARDED_ENDPOINTS = Map.of(
            // Social OAuth routes
            Pattern.compile("^/oauth2/authorization/google.*"), AuthModule.OAUTH2_GOOGLE,
            Pattern.compile("^/oauth2/authorization/github.*"), AuthModule.OAUTH2_GITHUB,
            Pattern.compile("^/oauth2/authorization/apple.*"), AuthModule.OAUTH2_APPLE,
            Pattern.compile("^/oauth2/authorization/linkedin.*"), AuthModule.OAUTH2_LINKEDIN,

            // Magic link routes
            Pattern.compile("^/auth/magic-link.*"), AuthModule.MAGIC_LINK,

            // Account factor APIs
            Pattern.compile("^/api/v1/factors/totp.*"), AuthModule.TOTP,
            Pattern.compile("^/api/v1/factors/passkey.*"), AuthModule.PASSKEY,

            // WebAuthn/Passkey routes
            Pattern.compile("^/webauthn/.*"), AuthModule.PASSKEY,

            // TOTP routes
            Pattern.compile("^/totp/.*"), AuthModule.TOTP
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Check if this path requires a specific module
        for (Map.Entry<Pattern, AuthModule> entry : GUARDED_ENDPOINTS.entrySet()) {
            if (entry.getKey().matcher(path).matches()) {
                AuthModule requiredModule = entry.getValue();

                // Check if module is enabled for the client
                if (!contextHolder.hasContext()) {
                    ClientContext resolvedContext = clientContextResolver.resolve(request);
                    if (resolvedContext.isResolved()) {
                        contextHolder.setContext(resolvedContext);
                    } else if (isContextOptional(request, path, requiredModule)) {
                        log.debug("Allowing {} without client context", path);
                        break;
                    } else {
                        log.warn("Blocked {} - no client context", path);
                        sendForbidden(response, "Missing client context. Please provide client_id.");
                        return;
                    }
                }

                ClientContext context = contextHolder.getContext();
                if (!context.isModuleEnabled(requiredModule)) {
                    log.warn("Blocked {} for client {} - module {} is disabled",
                            path, context.getClientId(), requiredModule);
                    sendForbidden(response, "Authentication method '" + requiredModule.getDisplayName() +
                            "' is not enabled for this client.");
                    return;
                }

                log.debug("Allowed {} for client {} - module {} is enabled",
                        path, context.getClientId(), requiredModule);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\": \"forbidden\", \"message\": \"%s\"}", message));
    }

    private boolean isContextOptional(HttpServletRequest request, String path, AuthModule requiredModule) {
        boolean explicitClientContext = hasExplicitClientContext(request);
        return (requiredModule == AuthModule.MAGIC_LINK && "/auth/magic-link/verify".equals(path))
                || (path.startsWith("/api/v1/factors/") && !explicitClientContext);
    }

    private boolean hasExplicitClientContext(HttpServletRequest request) {
        return hasText(request.getParameter("client_id"))
                || hasText(request.getParameter("pk"))
                || hasText(request.getHeader("X-Publishable-Key"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Only filter guarded endpoints
        return GUARDED_ENDPOINTS.keySet().stream()
                .noneMatch(pattern -> pattern.matcher(path).matches());
    }
}
