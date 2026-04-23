package com.arkil.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "arkil.ratelimit.backend", havingValue = "redis")
public class RedisRateLimitBackend implements RateLimitBackend {

    private final StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${arkil.ratelimit.redis.key-prefix:arkil:ratelimit}")
    private String keyPrefix;

    @Override
    public RateLimitBucket.RateLimitResult tryConsume(String namespace, String key, int maxRequests, long windowSeconds) {
        String redisKey = keyPrefix + ":" + namespace + ":" + key;
        Long currentCount = redisTemplate.opsForValue().increment(redisKey);
        if (currentCount == null) {
            currentCount = 1L;
        }

        if (currentCount == 1L) {
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }

        Long ttlSeconds = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        if (ttlSeconds == null || ttlSeconds < 0) {
            ttlSeconds = windowSeconds;
            redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
        }

        boolean allowed = currentCount <= maxRequests;
        return RateLimitBucket.RateLimitResult.builder()
                .allowed(allowed)
                .limit(maxRequests)
                .remaining((int) Math.max(0, maxRequests - currentCount))
                .resetAt(Instant.now().plusSeconds(ttlSeconds))
                .retryAfterSeconds(allowed ? 0 : ttlSeconds)
                .build();
    }
}
