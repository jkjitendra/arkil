package com.arkil.security;

/**
 * Exception thrown when a rate limit is exceeded.
 * Handled by GlobalExceptionHandler to return HTTP 429 with Retry-After header.
 */
public class RateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitExceededException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitExceededException(int retryAfterSeconds) {
        this("Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.", retryAfterSeconds);
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
