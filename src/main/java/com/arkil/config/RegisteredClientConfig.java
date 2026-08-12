package com.arkil.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
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
import java.util.List;
import java.util.Set;
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

    private static final String DASHBOARD_CLIENT_ID = "arkil-dashboard";

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
    ApplicationRunner clientBootstrap(RegisteredClientRepository repository,
                                      ArkilUrlProperties urlProperties,
                                      Environment environment) {
        return new ClientBootstrap(repository, urlProperties,
                !environment.matchesProfiles("prod", "production"));
    }

    @RequiredArgsConstructor
    static class ClientBootstrap implements ApplicationRunner {

        private final RegisteredClientRepository repository;
        private final ArkilUrlProperties urlProperties;
        private final boolean bootstrapDemoClient;

        @Override
        public void run(ApplicationArguments args) {
            bootstrapDashboardClient();
            if (bootstrapDemoClient) {
                bootstrapDemoClient();
            }
        }

        private void bootstrapDashboardClient() {
            RegisteredClient existingClient = repository.findByClientId(DASHBOARD_CLIENT_ID);
            if (existingClient != null) {
                reconcileDashboardRedirectUris(existingClient);
                return;
            }

            RegisteredClient dashboardClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(DASHBOARD_CLIENT_ID)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUris(uris -> uris.addAll(dashboardRedirectUris()))
                    .postLogoutRedirectUri(dashboardPostLogoutRedirectUri())
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

        /**
         * The built-in dashboard client is an Arkil-owned client, so its redirect
         * URI allow-list is safely reconciled to the active deployment. This both
         * repairs old registrations and removes stale localhost callbacks when a
         * database is promoted to production.
         */
        private void reconcileDashboardRedirectUris(RegisteredClient existingClient) {
            if (existingClient.getRedirectUris().equals(Set.copyOf(dashboardRedirectUris()))
                    && existingClient.getPostLogoutRedirectUris().equals(Set.of(dashboardPostLogoutRedirectUri()))) {
                log.debug("arkil-dashboard client already has the active deployment redirect URIs");
                return;
            }

            RegisteredClient.Builder updatedClient = RegisteredClient.withId(existingClient.getId())
                    .clientId(existingClient.getClientId())
                    .clientAuthenticationMethods(methods -> methods.addAll(existingClient.getClientAuthenticationMethods()))
                    .authorizationGrantTypes(types -> types.addAll(existingClient.getAuthorizationGrantTypes()))
                    .redirectUris(uris -> uris.addAll(dashboardRedirectUris()))
                    .postLogoutRedirectUris(uris -> uris.add(dashboardPostLogoutRedirectUri()))
                    .scopes(scopes -> scopes.addAll(existingClient.getScopes()))
                    .clientSettings(existingClient.getClientSettings())
                    .tokenSettings(existingClient.getTokenSettings());

            if (existingClient.getClientIdIssuedAt() != null) {
                updatedClient.clientIdIssuedAt(existingClient.getClientIdIssuedAt());
            }
            if (existingClient.getClientSecret() != null) {
                updatedClient.clientSecret(existingClient.getClientSecret());
            }
            if (existingClient.getClientSecretExpiresAt() != null) {
                updatedClient.clientSecretExpiresAt(existingClient.getClientSecretExpiresAt());
            }

            repository.save(updatedClient.build());
            log.info("Reconciled arkil-dashboard redirect URIs with the active deployment");
        }

        private List<String> dashboardRedirectUris() {
            return List.of(
                    urlProperties.dashboard() + "/callback",
                    urlProperties.dashboard() + "/silent-refresh"
            );
        }

        private String dashboardPostLogoutRedirectUri() {
            return urlProperties.dashboard() + "/";
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
