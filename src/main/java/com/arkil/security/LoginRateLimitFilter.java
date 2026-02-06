package com.arkil.security;

import com.arkil.audit.ActorType;
import com.arkil.audit.AuditEventType;
import com.arkil.audit.AuditOutcome;
import com.arkil.audit.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting filter for login endpoints.
 * Uses in-memory tracking (can be upgraded to Redis for distributed deployments).
 */
@Component
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final AuditService auditService;
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Value("${arkil.security.rate-limit.max-attempts:10}")
    private int maxAttempts;

    @Value("${arkil.security.rate-limit.window-seconds:300}")
    private int windowSeconds;

    public LoginRateLimitFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ip = extractIpAddress(request);
        RateLimitBucket bucket = buckets.compute(ip, (key, existing) -> {
            if (existing == null || existing.isExpired(windowSeconds)) {
                return new RateLimitBucket();
            }
            return existing;
        });

        if (bucket.incrementAndCheck(maxAttempts)) {
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

    /**
     * Simple bucket for tracking request counts per time window.
     */
    private static class RateLimitBucket {
        private final Instant createdAt = Instant.now();
        private final AtomicInteger count = new AtomicInteger(0);

        boolean isExpired(int windowSeconds) {
            return Duration.between(createdAt, Instant.now()).getSeconds() > windowSeconds;
        }

        boolean incrementAndCheck(int maxAttempts) {
            return count.incrementAndGet() > maxAttempts;
        }
    }
}
