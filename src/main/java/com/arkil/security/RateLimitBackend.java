package com.arkil.security;

public interface RateLimitBackend {

    RateLimitBucket.RateLimitResult tryConsume(String namespace, String key, int maxRequests, long windowSeconds);

    default void cleanup() {
    }
}
