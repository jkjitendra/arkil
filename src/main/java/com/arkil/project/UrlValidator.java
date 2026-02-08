package com.arkil.project;

import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates CORS origins and redirect URIs.
 */
@Component
public class UrlValidator {

    // Localhost patterns for development
    private static final Pattern LOCALHOST_PATTERN = Pattern.compile(
            "^https?://(localhost|127\\.0\\.0\\.1)(:\\d+)?$"
    );

    /**
     * Validate CORS allowed origins.
     * Rules:
     * - Must be valid URL format
     * - Must be https:// (or http://localhost for dev)
     * - No path allowed (origin only: scheme://host[:port])
     */
    public ValidationResult validateOrigins(List<String> origins, boolean allowLocalhost) {
        if (origins == null || origins.isEmpty()) {
            return ValidationResult.success();
        }

        List<String> errors = new ArrayList<>();

        for (String origin : origins) {
            if (origin == null || origin.isBlank()) {
                errors.add("Empty origin not allowed");
                continue;
            }

            // Check localhost
            if (LOCALHOST_PATTERN.matcher(origin).matches()) {
                if (!allowLocalhost) {
                    errors.add("Localhost not allowed in production: " + origin);
                }
                continue;
            }

            // Must be https://
            if (!origin.startsWith("https://")) {
                errors.add("Origin must use HTTPS: " + origin);
                continue;
            }

            // Validate URL format
            try {
                URL url = new URL(origin);

                // No path allowed (only scheme://host[:port])
                String path = url.getPath();
                if (path != null && !path.isEmpty() && !path.equals("/")) {
                    errors.add("Origin must not contain path: " + origin);
                }

                // No query or fragment
                if (url.getQuery() != null || url.getRef() != null) {
                    errors.add("Origin must not contain query or fragment: " + origin);
                }
            } catch (MalformedURLException e) {
                errors.add("Invalid URL format: " + origin);
            }
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * Validate OAuth redirect URIs.
     * Rules:
     * - Must be valid URL format
     * - Must be https:// in production (localhost allowed in dev)
     * - Path is allowed for redirect URIs
     * - No fragment allowed (OAuth spec)
     */
    public ValidationResult validateRedirectUris(List<String> uris, boolean allowLocalhost) {
        if (uris == null || uris.isEmpty()) {
            return ValidationResult.success();
        }

        List<String> errors = new ArrayList<>();

        for (String uri : uris) {
            if (uri == null || uri.isBlank()) {
                errors.add("Empty redirect URI not allowed");
                continue;
            }

            // Check localhost
            if (uri.startsWith("http://localhost") || uri.startsWith("http://127.0.0.1")) {
                if (!allowLocalhost) {
                    errors.add("Localhost not allowed in production: " + uri);
                }
                continue;
            }

            // Must be https:// for non-localhost
            if (!uri.startsWith("https://")) {
                errors.add("Redirect URI must use HTTPS: " + uri);
                continue;
            }

            // Validate URL format
            try {
                URL url = new URL(uri);

                // No fragment allowed (OAuth 2.0 spec)
                if (url.getRef() != null) {
                    errors.add("Redirect URI must not contain fragment: " + uri);
                }
            } catch (MalformedURLException e) {
                errors.add("Invalid URL format: " + uri);
            }
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }
}
