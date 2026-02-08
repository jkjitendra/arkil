package com.arkil.policy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that resolves and stores the client context for each request.
 * Runs early in the filter chain to make context available to other components.
 */
@Component
@Order(-100) // Run early
@RequiredArgsConstructor
@Slf4j
public class ClientContextFilter extends OncePerRequestFilter {

    private final ClientContextResolver resolver;
    private final ClientContextHolder holder;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Resolve client context
        ClientContext context = resolver.resolve(request);
        holder.setContext(context);

        // Store as request attribute for Thymeleaf
        request.setAttribute("clientContext", context);

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip for static resources, API endpoints, actuator, and OAuth2 authorization server endpoints
        return path.startsWith("/css/") ||
                path.startsWith("/js/") ||
                path.startsWith("/images/") ||
                path.startsWith("/api/") ||
                path.startsWith("/actuator/") ||
                path.startsWith("/h2-console/") ||
                path.startsWith("/oauth2/") ||
                path.startsWith("/.well-known/") ||
                path.startsWith("/connect/") ||
                path.equals("/login") ||
                path.equals("/error") ||
                path.equals("/userinfo") ||
                path.equals("/favicon.ico");
    }
}
