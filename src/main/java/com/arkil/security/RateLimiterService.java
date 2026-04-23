package com.arkil.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Rate limiter service with multiple bucket types:
 * - Per IP: Prevent single IP from flooding
 * - Per Email: Prevent enumeration attacks
 * - Per Project: Prevent single project from exhausting resources
 */
@Slf4j
@Service
public class RateLimiterService {

    private final RateLimitBackend backend;
    private final int ipMax;
    private final int ipWindow;
    private final int emailMax;
    private final int emailWindow;
    private final int projectMax;
    private final int projectWindow;
    private final int loginMax;
    private final int loginWindow;
    private final int hostedLoginMaxAttempts;
    private final int hostedLoginWindowSeconds;

    public RateLimiterService(
            RateLimitBackend backend,
            @Value("${arkil.ratelimit.ip.max:100}") int ipMax,
            @Value("${arkil.ratelimit.ip.window:60}") int ipWindow,
            @Value("${arkil.ratelimit.email.max:5}") int emailMax,
            @Value("${arkil.ratelimit.email.window:300}") int emailWindow,
            @Value("${arkil.ratelimit.project.max:1000}") int projectMax,
            @Value("${arkil.ratelimit.project.window:60}") int projectWindow,
            @Value("${arkil.ratelimit.login.max:5}") int loginMax,
            @Value("${arkil.ratelimit.login.window:300}") int loginWindow,
            @Value("${arkil.security.rate-limit.max-attempts:10}") int hostedLoginMaxAttempts,
            @Value("${arkil.security.rate-limit.window-seconds:300}") int hostedLoginWindowSeconds) {
        this.backend = backend;
        this.ipMax = ipMax;
        this.ipWindow = ipWindow;
        this.emailMax = emailMax;
        this.emailWindow = emailWindow;
        this.projectMax = projectMax;
        this.projectWindow = projectWindow;
        this.loginMax = loginMax;
        this.loginWindow = loginWindow;
        this.hostedLoginMaxAttempts = hostedLoginMaxAttempts;
        this.hostedLoginWindowSeconds = hostedLoginWindowSeconds;

        log.info("Rate limiter initialized: IP={}/{}, Email={}/{}, Project={}/{}, Login={}/{}",
                ipMax, ipWindow, emailMax, emailWindow, projectMax, projectWindow, loginMax, loginWindow);
    }

    /**
     * Check IP rate limit.
     */
    public RateLimitBucket.RateLimitResult checkIp(String ip) {
        return backend.tryConsume("ip", ip, ipMax, ipWindow);
    }

    /**
     * Check email rate limit (for password reset, magic link, etc.)
     */
    public RateLimitBucket.RateLimitResult checkEmail(String email) {
        return backend.tryConsume("email", email.toLowerCase(), emailMax, emailWindow);
    }

    /**
     * Check project rate limit.
     */
    public RateLimitBucket.RateLimitResult checkProject(String projectId) {
        return backend.tryConsume("project", projectId, projectMax, projectWindow);
    }

    /**
     * Check login attempt rate limit.
     * Key is combination of username and project to prevent account lockout attacks.
     */
    public RateLimitBucket.RateLimitResult checkLogin(String username, String projectId) {
        String key = username.toLowerCase() + "@" + projectId;
        return backend.tryConsume("login", key, loginMax, loginWindow);
    }

    public RateLimitBucket.RateLimitResult checkHostedLoginIp(String ip) {
        return backend.tryConsume("hosted-login-ip", ip, hostedLoginMaxAttempts, hostedLoginWindowSeconds);
    }

    /**
     * Combined check for auth endpoints.
     * Returns the most restrictive result.
     */
    public RateLimitBucket.RateLimitResult checkAuthRequest(String ip, String email, String projectId) {
        RateLimitBucket.RateLimitResult ipResult = checkIp(ip);
        if (!ipResult.isAllowed()) {
            log.warn("IP rate limit exceeded: {}", ip);
            return ipResult;
        }

        if (email != null) {
            RateLimitBucket.RateLimitResult emailResult = checkEmail(email);
            if (!emailResult.isAllowed()) {
                log.warn("Email rate limit exceeded: {}", email);
                return emailResult;
            }
        }

        if (projectId != null) {
            RateLimitBucket.RateLimitResult projectResult = checkProject(projectId);
            if (!projectResult.isAllowed()) {
                log.warn("Project rate limit exceeded: {}", projectId);
                return projectResult;
            }
        }

        return ipResult; // Return IP result with remaining headers
    }

    /**
     * Cleanup expired entries periodically.
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void cleanup() {
        backend.cleanup();
        log.debug("Rate limit buckets cleaned up");
    }
}
