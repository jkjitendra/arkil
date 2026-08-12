package com.arkil.security;

import com.arkil.client.ClientAuthPolicy;
import com.arkil.client.ClientAuthPolicyRepository;
import com.arkil.config.ArkilUrlProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.ArrayList;
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
    private final ArkilUrlProperties urlProperties;

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
                    config.setAllowedOrigins(fallbackOrigins());
                    log.debug("No CORS origins configured for client {}, using platform defaults", clientId);
                }
            } else {
                config.setAllowedOrigins(fallbackOrigins());
            }
        } else {
            // This covers the platform dashboard's own API requests.
            config.setAllowedOrigins(fallbackOrigins());
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

    private List<String> fallbackOrigins() {
        List<String> origins = new ArrayList<>(urlProperties.platformCorsOrigins());
        origins.addAll(urlProperties.developmentCorsOrigins());
        return origins.stream().distinct().toList();
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
