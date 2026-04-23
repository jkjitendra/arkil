package com.arkil.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "arkil.ratelimit.backend", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimitBackend implements RateLimitBackend {

    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitBucket.RateLimitResult tryConsume(String namespace, String key, int maxRequests, long windowSeconds) {
        String bucketKey = namespace + ":" + maxRequests + ":" + windowSeconds;
        RateLimitBucket bucket = buckets.computeIfAbsent(bucketKey, ignored -> new RateLimitBucket(maxRequests, windowSeconds));
        return bucket.tryConsume(key);
    }

    @Override
    public void cleanup() {
        buckets.values().forEach(RateLimitBucket::cleanup);
    }
}
