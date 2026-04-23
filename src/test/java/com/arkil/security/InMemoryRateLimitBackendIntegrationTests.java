package com.arkil.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = "arkil.ratelimit.backend=memory")
class InMemoryRateLimitBackendIntegrationTests {

    @Autowired
    private RateLimitBackend backend;

    @Test
    void usesInMemoryBackendByDefault() {
        assertInstanceOf(InMemoryRateLimitBackend.class, backend);
    }
}
