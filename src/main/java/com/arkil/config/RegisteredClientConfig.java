package com.arkil.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.UUID;

/**
 * Database-backed OAuth2 client registration.
 * Uses Spring Authorization Server's JdbcRegisteredClientRepository.
 *
 * Bootstraps the arkil-dashboard and demo-client into the database on first startup.
 * Project-specific clients are created dynamically by RegisteredClientBridgeService.
 */
@Configuration
@Slf4j
public class RegisteredClientConfig {

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * Bootstrap core clients into the database on first startup.
     */
    @Bean
    @Order(5) // Run before DemoDataBootstrap (order 10)
    ApplicationRunner clientBootstrap(RegisteredClientRepository repository) {
        return new ClientBootstrap(repository);
    }

    @RequiredArgsConstructor
    static class ClientBootstrap implements ApplicationRunner {

        private final RegisteredClientRepository repository;

        @Override
        public void run(ApplicationArguments args) {
            bootstrapDashboardClient();
            bootstrapDemoClient();
        }

        private void bootstrapDashboardClient() {
            if (repository.findByClientId("arkil-dashboard") != null) {
                log.debug("arkil-dashboard client already exists in database");
                return;
            }

            RegisteredClient dashboardClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("arkil-dashboard")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost:5173/callback")
                    .redirectUri("http://localhost:5173/silent-refresh")
                    .postLogoutRedirectUri("http://localhost:5173/")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .scope("arkil:admin")
                    .clientSettings(ClientSettings.builder()
                            .requireAuthorizationConsent(false)
                            .requireProofKey(true)
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofMinutes(30))
                            .refreshTokenTimeToLive(Duration.ofDays(1))
                            .reuseRefreshTokens(false)
                            .build())
                    .build();

            repository.save(dashboardClient);
            log.info("Bootstrapped arkil-dashboard client into database");
        }

        private void bootstrapDemoClient() {
            if (repository.findByClientId("demo-client") != null) {
                log.debug("demo-client already exists in database");
                return;
            }

            RegisteredClient demoClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("demo-client")
                    .clientSecret("{noop}demo-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .redirectUri("http://127.0.0.1:8080/login/oauth2/code/demo-client")
                    .redirectUri("http://localhost:8080/authorized")
                    .postLogoutRedirectUri("http://127.0.0.1:8080/")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .scope("arkil:admin")
                    .clientSettings(ClientSettings.builder()
                            .requireAuthorizationConsent(true)
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofHours(1))
                            .refreshTokenTimeToLive(Duration.ofDays(7))
                            .build())
                    .build();

            repository.save(demoClient);
            log.info("Bootstrapped demo-client into database");
        }
    }
}
