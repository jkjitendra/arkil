package com.arkil.config;

import com.arkil.auth.DashboardStaleLogoutAuthenticationProvider;
import com.arkil.security.ProjectCorsConfigurationSource;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Spring Authorization Server configuration.
 * Provides the OAuth2/OIDC endpoints (authorize, token, jwks, etc.)
 */
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    private final ProjectCorsConfigurationSource corsConfigurationSource;
    private final ArkilUrlProperties urlProperties;
    private final Environment environment;

    @Value("${arkil.jwt.jwk-set-json:}")
    private String configuredJwkSetJson;

    public AuthorizationServerConfig(ProjectCorsConfigurationSource corsConfigurationSource,
                                    ArkilUrlProperties urlProperties,
                                    Environment environment) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.urlProperties = urlProperties;
        this.environment = environment;
    }

    /**
     * SecurityFilterChain #1: Authorization Server endpoints.
     * Handles /oauth2/authorize, /oauth2/token, /.well-known/*, etc.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService) throws Exception {
        // Create and configure the OAuth2 Authorization Server configurer
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        // Enable OIDC with logout support
        authorizationServerConfigurer.oidc(oidc -> oidc
                .logoutEndpoint(logout -> logout.authenticationProviders(providers -> providers.add(0,
                        new DashboardStaleLogoutAuthenticationProvider(jwtDecoder,
                                registeredClientRepository, authorizationService))))
        );

        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                // Enable CORS for OIDC discovery and token endpoints
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher())
                )
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                )
                .apply(authorizationServerConfigurer);

        return http.build();
    }

    /**
     * JWT decoder for resource server functionality.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(urlProperties.authServer() + "/oauth2/jwks").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(urlProperties.authServer()));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        // The JWK endpoint also publishes previous public keys for validation.
        // Only the one private key may be selected for new signatures.
        encoder.setJwkSelector(keys -> keys.stream()
                .filter(JWK::isPrivate)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No active JWT signing key")));
        return encoder;
    }

    /**
     * JWK source for signing tokens.
     * Production instances share a durable JWK set supplied as a secret. Keep
     * old public keys in the set during rotation until issued tokens expire.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        if (configuredJwkSetJson != null && !configuredJwkSetJson.isBlank()) {
            try {
                JWKSet configured = JWKSet.parse(configuredJwkSetJson);
                long signingKeys = configured.getKeys().stream()
                        .filter(JWK::isPrivate)
                        .filter(key -> key instanceof RSAKey && key.getKeyID() != null
                                && !key.getKeyID().isBlank())
                        .count();
                if (signingKeys != 1 || configured.getKeys().stream()
                        .anyMatch(key -> !(key instanceof RSAKey) || key.getKeyID() == null
                                || key.getKeyID().isBlank())) {
                    throw new IllegalStateException("JWT JWK set must contain exactly one private RSA signing key and named RSA public keys");
                }
                return new ImmutableJWKSet<>(configured);
            } catch (java.text.ParseException invalidJson) {
                throw new IllegalStateException("Invalid arkil.jwt.jwk-set-json", invalidJson);
            }
        }
        if (environment.matchesProfiles("prod", "production")) {
            throw new IllegalStateException("ARKIL_JWT_JWK_SET_JSON is required in production");
        }

        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();

        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }

    /**
     * Authorization server settings.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(urlProperties.authServer())
                .build();
    }
}
