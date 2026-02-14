package com.arkil.project;

import com.arkil.client.AuthModule;
import com.arkil.client.ClientAuthPolicy;
import com.arkil.client.ClientAuthPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bridge between Project entities and Spring Authorization Server's RegisteredClient.
 *
 * When a project is created, this service:
 * 1. Creates a RegisteredClient (public PKCE client) with client_id = proj_{slug}
 * 2. Creates a default ClientAuthPolicy with EMAIL_PASSWORD enabled
 *
 * When a project is updated, it syncs redirect URIs and origins.
 * When a project is deleted, it removes the RegisteredClient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisteredClientBridgeService {

    private final RegisteredClientRepository registeredClientRepository;
    private final ClientAuthPolicyRepository clientAuthPolicyRepository;

    private static final String CLIENT_ID_PREFIX = "proj_";

    /**
     * Create a RegisteredClient and default auth policy for a new project.
     *
     * @return the Spring internal RegisteredClient ID (stored on Project entity)
     */
    @Transactional
    public String createRegisteredClientForProject(Project project) {
        String clientId = CLIENT_ID_PREFIX + project.getSlug();

        // Ensure no existing client with this ID
        if (registeredClientRepository.findByClientId(clientId) != null) {
            throw new IllegalStateException("RegisteredClient already exists for client_id: " + clientId);
        }

        String internalId = UUID.randomUUID().toString();

        // Build redirect URIs from project config, with sensible defaults
        RegisteredClient.Builder builder = RegisteredClient.withId(internalId)
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // Public client (PKCE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .reuseRefreshTokens(false)
                        .build());

        // Add redirect URIs from project
        List<String> redirectUris = project.getRedirectUris();
        if (redirectUris != null && !redirectUris.isEmpty()) {
            redirectUris.forEach(builder::redirectUri);
        } else {
            // Default redirect URI for development
            builder.redirectUri("http://localhost:3000/callback");
        }

        RegisteredClient registeredClient = builder.build();
        registeredClientRepository.save(registeredClient);

        // Create default auth policy with EMAIL_PASSWORD enabled
        ClientAuthPolicy policy = ClientAuthPolicy.builder()
                .registeredClientInternalId(internalId)
                .clientId(clientId)
                .enabledModules(Set.of(AuthModule.EMAIL_PASSWORD))
                .allowedOrigins(project.getAllowedOrigins() != null ? new ArrayList<>(project.getAllowedOrigins()) : List.of())
                .redirectUris(redirectUris != null ? new ArrayList<>(redirectUris) : List.of())
                .build();

        clientAuthPolicyRepository.save(policy);

        log.info("Created RegisteredClient {} and default auth policy for project {}", clientId, project.getSlug());
        return internalId;
    }

    /**
     * Sync redirect URIs and origins from a project update to its RegisteredClient.
     */
    @Transactional
    public void updateRegisteredClient(Project project) {
        if (project.getRegisteredClientId() == null) {
            log.warn("Project {} has no registered client ID, skipping sync", project.getSlug());
            return;
        }

        String clientId = CLIENT_ID_PREFIX + project.getSlug();
        RegisteredClient existing = registeredClientRepository.findByClientId(clientId);
        if (existing == null) {
            log.warn("RegisteredClient not found for client_id: {}", clientId);
            return;
        }

        // Rebuild with updated redirect URIs
        RegisteredClient updated = RegisteredClient.withId(existing.getId())
                .clientId(existing.getClientId())
                .clientAuthenticationMethods(methods -> methods.addAll(existing.getClientAuthenticationMethods()))
                .authorizationGrantTypes(types -> types.addAll(existing.getAuthorizationGrantTypes()))
                .scopes(scopes -> scopes.addAll(existing.getScopes()))
                .clientSettings(existing.getClientSettings())
                .tokenSettings(existing.getTokenSettings())
                .redirectUris(uris -> {
                    if (project.getRedirectUris() != null && !project.getRedirectUris().isEmpty()) {
                        uris.addAll(project.getRedirectUris());
                    } else {
                        uris.add("http://localhost:3000/callback");
                    }
                })
                .build();

        registeredClientRepository.save(updated);

        // Update the auth policy too (origins and redirect URIs)
        clientAuthPolicyRepository.findByClientId(clientId).ifPresent(policy -> {
            if (project.getAllowedOrigins() != null) {
                policy.setAllowedOrigins(new ArrayList<>(project.getAllowedOrigins()));
            }
            if (project.getRedirectUris() != null) {
                policy.setRedirectUris(new ArrayList<>(project.getRedirectUris()));
            }
            clientAuthPolicyRepository.save(policy);
        });

        log.info("Updated RegisteredClient and auth policy for project {}", project.getSlug());
    }

    /**
     * Get the public client_id for a project slug.
     */
    public String getClientIdForProject(String slug) {
        return CLIENT_ID_PREFIX + slug;
    }
}
