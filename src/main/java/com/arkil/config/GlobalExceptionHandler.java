package com.arkil.config;

import com.arkil.security.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler for REST API.
 * Converts exceptions to proper HTTP responses with structured error bodies.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation errors from @Valid annotations.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();

        log.debug("Validation errors: {}", errors);

        return ResponseEntity.badRequest().body(Map.of(
                "error", "validation_error",
                "message", "Validation failed",
                "messages", errors,
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Handle IllegalArgumentException - typically from service layer validation.
     * This includes "slug already exists", "not found" errors, etc.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage();
        log.debug("IllegalArgumentException: {}", message);

        // Determine appropriate status code based on message content
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String errorCode = "bad_request";

        if (message != null) {
            if (message.contains("already exists")) {
                status = HttpStatus.CONFLICT;
                errorCode = "conflict";
            } else if (message.contains("not found")) {
                status = HttpStatus.NOT_FOUND;
                errorCode = "not_found";
            }
        }

        return ResponseEntity.status(status).body(Map.of(
                "error", errorCode,
                "message", message != null ? message : "Invalid request",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Handle SecurityException - auth/permission errors.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        log.warn("SecurityException: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "forbidden",
                "message", ex.getMessage() != null ? ex.getMessage() : "Access denied",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Handle IllegalStateException - invalid operation in current state.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.debug("IllegalStateException: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "invalid_state",
                "message", ex.getMessage() != null ? ex.getMessage() : "Operation not allowed in current state",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Handle missing static resources (e.g. favicon.ico) without logging stack traces.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "not_found",
                "message", "Resource not found",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Handle rate limit exceeded — returns 429 with Retry-After header.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(Map.of(
                        "error", "rate_limit_exceeded",
                        "message", ex.getMessage(),
                        "retryAfter", ex.getRetryAfterSeconds(),
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Handle OAuth2 authentication errors (e.g., invalid token, expired token).
     */
    @ExceptionHandler(OAuth2AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleOAuth2AuthenticationException(OAuth2AuthenticationException ex) {
        log.warn("OAuth2 authentication error: {}", ex.getMessage());

        String errorCode = ex.getError() != null ? ex.getError().getErrorCode() : "oauth_error";

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "error", "oauth_error",
                "message", ex.getMessage() != null ? ex.getMessage() : "OAuth2 authentication failed",
                "oauthErrorCode", errorCode,
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Handle OAuth2 authorization errors (e.g., provider unavailable, bad config).
     */
    @ExceptionHandler(OAuth2AuthorizationException.class)
    public ResponseEntity<Map<String, Object>> handleOAuth2AuthorizationException(OAuth2AuthorizationException ex) {
        log.error("OAuth2 provider error: {}", ex.getMessage());

        String errorCode = ex.getError() != null ? ex.getError().getErrorCode() : "oauth_provider_error";

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "oauth_provider_error",
                "message", "OAuth2 provider error. Please check your provider configuration.",
                "oauthErrorCode", errorCode,
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Handle Spring Security's AccessDeniedException for consistent JSON responses.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "access_denied",
                "message", ex.getMessage() != null ? ex.getMessage() : "Access denied",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Handle unsupported HTTP methods.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of(
                "error", "method_not_allowed",
                "message", "HTTP method " + ex.getMethod() + " is not supported for this endpoint",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Catch-all for unexpected exceptions.
     * Logs full stack trace but returns generic message to client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "internal_error",
                "message", "An unexpected error occurred. Please try again later.",
                "timestamp", Instant.now().toString()
        ));
    }
}

