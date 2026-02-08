package com.arkil.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
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
