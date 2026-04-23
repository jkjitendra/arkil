package com.arkil.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = {
        "arkil.ratelimit.backend=redis",
        "management.health.redis.enabled=false"
})
class RedisRateLimitBackendIntegrationTests {

    @Autowired
    private RateLimitBackend backend;

    @Test
    void usesRedisBackendWhenConfigured() {
        assertInstanceOf(RedisRateLimitBackend.class, backend);
    }
}
