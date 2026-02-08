package com.arkil.security;

import com.arkil.client.ClientAuthPolicy;
import com.arkil.client.ClientAuthPolicyRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Per-project CORS configuration source.
 * Reads allowed origins from the client policy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCorsConfigurationSource implements CorsConfigurationSource {

    private final ClientAuthPolicyRepository policyRepository;

    // Default allowed origins for development
    private static final List<String> DEV_ORIGINS = Arrays.asList(
            "http://localhost:3000",
            "http://localhost:5173",  // Vite dev server (dashboard)
            "http://localhost:8080",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:8080"
    );

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        CorsConfiguration config = new CorsConfiguration();

        // Get client_id from request (header, query param, or path)
        String clientId = extractClientId(request);

        if (clientId != null) {
            Optional<ClientAuthPolicy> policyOpt = policyRepository.findByClientId(clientId);
            if (policyOpt.isPresent()) {
                ClientAuthPolicy policy = policyOpt.get();
                List<String> allowedOrigins = policy.getAllowedOrigins();

                if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
                    config.setAllowedOrigins(allowedOrigins);
                    log.debug("Using project CORS origins for client {}: {}", clientId, allowedOrigins);
                } else {
                    // Fall back to dev origins if none configured
                    config.setAllowedOrigins(DEV_ORIGINS);
                    log.debug("No CORS origins configured for client {}, using dev defaults", clientId);
                }
            } else {
                config.setAllowedOrigins(DEV_ORIGINS);
            }
        } else {
            // No client context, use dev defaults
            config.setAllowedOrigins(DEV_ORIGINS);
        }

        // Standard CORS settings
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Arkil-Key",
                "X-Arkil-Client-ID",
                "X-Arkil-CSRF",
                "X-Requested-With"
        ));
        config.setExposedHeaders(Arrays.asList(
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        return config;
    }

    private String extractClientId(HttpServletRequest request) {
        // Try header first
        String clientId = request.getHeader("X-Arkil-Client-ID");
        if (clientId != null && !clientId.isEmpty()) {
            return clientId;
        }

        // Try query parameter
        clientId = request.getParameter("client_id");
        if (clientId != null && !clientId.isEmpty()) {
            return clientId;
        }

        // Could also extract from path for certain patterns
        return null;
    }
}
