package com.arkil.security;

import com.arkil.audit.ActorType;
import com.arkil.audit.AuditEventType;
import com.arkil.audit.AuditOutcome;
import com.arkil.audit.AuditService;
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

/**
 * Rate limiting filter for login endpoints.
 * Uses in-memory tracking (can be upgraded to Redis for distributed deployments).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final AuditService auditService;
    private final RateLimiterService rateLimiterService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ip = extractIpAddress(request);
        RateLimitBucket.RateLimitResult result = rateLimiterService.checkHostedLoginIp(ip);
        if (!result.isAllowed()) {
            // Rate limit exceeded
            log.warn("Rate limit exceeded for IP: {}", ip);
            auditService.logEvent(
                    AuditEventType.RATE_LIMIT_EXCEEDED,
                    ip,
                    ActorType.SYSTEM,
                    "/login",
                    AuditOutcome.BLOCKED,
                    "Too many login attempts",
                    request
            );

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            response.getWriter().write("""
                    {"error": "too_many_requests", "message": "Rate limit exceeded. Please try again later."}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only filter POST to /login
        return !("POST".equalsIgnoreCase(request.getMethod())
                && "/login".equals(request.getServletPath()));
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
