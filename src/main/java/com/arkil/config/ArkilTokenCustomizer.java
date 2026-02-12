package com.arkil.config;

import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Customizes JWT access tokens with Arkil-specific claims:
 * - tenant_id: the user's tenant UUID
 * - roles: the user's role names
 * - email: the user's email
 * - display_name: the user's display name
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ArkilTokenCustomizer {

    private final UserRepository userRepository;

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Authentication principal = context.getPrincipal();
                String principalName = principal.getName();

                // The principal name is the user's UUID (set by ArkilUserDetailsService)
                try {
                    UUID userId = UUID.fromString(principalName);
                    Optional<ArkilUser> userOpt = userRepository.findById(userId);

                    if (userOpt.isPresent()) {
                        ArkilUser user = userOpt.get();
                        context.getClaims()
                                .claim("tenant_id", user.getTenant().getId().toString())
                                .claim("email", user.getEmail())
                                .claim("display_name", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());

                        Set<String> roles = user.getRoles().stream()
                                .map(role -> role.getName())
                                .collect(Collectors.toSet());
                        context.getClaims().claim("roles", roles);
                    }
                } catch (IllegalArgumentException e) {
                    // Principal name is not a UUID (e.g., client credentials flow)
                    // Add roles from granted authorities
                    Set<String> roles = principal.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .filter(a -> a.startsWith("ROLE_"))
                            .map(a -> a.substring(5))
                            .collect(Collectors.toSet());
                    if (!roles.isEmpty()) {
                        context.getClaims().claim("roles", roles);
                    }
                }
            }
        };
    }
}
