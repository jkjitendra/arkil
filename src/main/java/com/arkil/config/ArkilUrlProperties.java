package com.arkil.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Public URLs used by browser-facing Arkil flows.
 *
 * <p>These values describe the URLs that users' browsers can reach, rather
 * than an internal container or load-balancer address. Keeping them in one
 * place prevents an OAuth issuer, callback, email link, or CORS policy from
 * accidentally referring to a local development server in production.</p>
 */
@Validated
@ConfigurationProperties(prefix = "arkil.urls")
public record ArkilUrlProperties(
        @NotBlank String authServer,
        @NotBlank String dashboard,
        List<String> platformCorsOrigins,
        List<String> developmentCorsOrigins
) {
    public ArkilUrlProperties {
        authServer = normalizeBaseUrl(authServer, "arkil.urls.auth-server");
        dashboard = normalizeBaseUrl(dashboard, "arkil.urls.dashboard");
        platformCorsOrigins = platformCorsOrigins == null ? List.of() : List.copyOf(platformCorsOrigins);
        developmentCorsOrigins = developmentCorsOrigins == null ? List.of() : List.copyOf(developmentCorsOrigins);
    }

    private static String normalizeBaseUrl(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must be configured");
        }

        String normalized = value.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalArgumentException(propertyName + " must start with http:// or https://");
        }

        return normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }
}
