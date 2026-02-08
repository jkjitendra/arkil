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

    // IP-based rate limits (more restrictive)
    private final RateLimitBucket ipBucket;

    // Email-based rate limits (for auth endpoints)
    private final RateLimitBucket emailBucket;

    // Project-based rate limits
    private final RateLimitBucket projectBucket;

    // Login attempt limits (per username@project)
    private final RateLimitBucket loginBucket;

    public RateLimiterService(
            @Value("${arkil.ratelimit.ip.max:100}") int ipMax,
            @Value("${arkil.ratelimit.ip.window:60}") int ipWindow,
            @Value("${arkil.ratelimit.email.max:5}") int emailMax,
            @Value("${arkil.ratelimit.email.window:300}") int emailWindow,
            @Value("${arkil.ratelimit.project.max:1000}") int projectMax,
            @Value("${arkil.ratelimit.project.window:60}") int projectWindow,
            @Value("${arkil.ratelimit.login.max:5}") int loginMax,
            @Value("${arkil.ratelimit.login.window:300}") int loginWindow) {

        this.ipBucket = new RateLimitBucket(ipMax, ipWindow);
        this.emailBucket = new RateLimitBucket(emailMax, emailWindow);
        this.projectBucket = new RateLimitBucket(projectMax, projectWindow);
        this.loginBucket = new RateLimitBucket(loginMax, loginWindow);

        log.info("Rate limiter initialized: IP={}/{}, Email={}/{}, Project={}/{}, Login={}/{}",
                ipMax, ipWindow, emailMax, emailWindow, projectMax, projectWindow, loginMax, loginWindow);
    }

    /**
     * Check IP rate limit.
     */
    public RateLimitBucket.RateLimitResult checkIp(String ip) {
        return ipBucket.tryConsume(ip);
    }

    /**
     * Check email rate limit (for password reset, magic link, etc.)
     */
    public RateLimitBucket.RateLimitResult checkEmail(String email) {
        return emailBucket.tryConsume(email.toLowerCase());
    }

    /**
     * Check project rate limit.
     */
    public RateLimitBucket.RateLimitResult checkProject(String projectId) {
        return projectBucket.tryConsume(projectId);
    }

    /**
     * Check login attempt rate limit.
     * Key is combination of username and project to prevent account lockout attacks.
     */
    public RateLimitBucket.RateLimitResult checkLogin(String username, String projectId) {
        String key = username.toLowerCase() + "@" + projectId;
        return loginBucket.tryConsume(key);
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
        ipBucket.cleanup();
        emailBucket.cleanup();
        projectBucket.cleanup();
        loginBucket.cleanup();
        log.debug("Rate limit buckets cleaned up");
    }
}
