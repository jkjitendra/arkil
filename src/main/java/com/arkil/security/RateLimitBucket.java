package com.arkil.security;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiter with multiple bucket types.
 * For production, use Redis-based implementation.
 */
public class RateLimitBucket {

    private final int maxRequests;
    private final long windowMillis;
    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    public RateLimitBucket(int maxRequests, long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000;
    }

    /**
     * Check if request is allowed and consume a token.
     *
     * @param key The bucket key (e.g., IP, email, project ID)
     * @return RateLimitResult with allowed status and remaining tokens
     */
    public RateLimitResult tryConsume(String key) {
        BucketEntry entry = buckets.compute(key, (k, existing) -> {
            long now = System.currentTimeMillis();

            if (existing == null || existing.windowStart + windowMillis < now) {
                // New window
                return new BucketEntry(now, new AtomicInteger(1));
            }

            // Same window, increment count
            existing.count.incrementAndGet();
            return existing;
        });

        int currentCount = entry.count.get();
        boolean allowed = currentCount <= maxRequests;
        long resetAt = entry.windowStart + windowMillis;

        return RateLimitResult.builder()
                .allowed(allowed)
                .limit(maxRequests)
                .remaining(Math.max(0, maxRequests - currentCount))
                .resetAt(Instant.ofEpochMilli(resetAt))
                .retryAfterSeconds(allowed ? 0 : (resetAt - System.currentTimeMillis()) / 1000)
                .build();
    }

    /**
     * Clean up expired entries (call periodically).
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry ->
                entry.getValue().windowStart + windowMillis < now);
    }

    private static class BucketEntry {
        final long windowStart;
        final AtomicInteger count;

        BucketEntry(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }

    @Data
    @Builder
    public static class RateLimitResult {
        private boolean allowed;
        private int limit;
        private int remaining;
        private Instant resetAt;
        private long retryAfterSeconds;
    }
}
