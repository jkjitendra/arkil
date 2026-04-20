package com.arkil.config;

import com.arkil.policy.ClientContext;
import com.arkil.policy.ClientContextHolder;
import com.arkil.project.Project;
import com.arkil.project.ProjectOAuthProvider;
import com.arkil.project.ProjectOAuthProviderRepository;
import com.arkil.project.ProjectRepository;
import com.arkil.security.SecretEncryptionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic ClientRegistrationRepository that resolves OAuth2 client registrations
 * based on the current client context (project).
 *
 * When an end-user initiates social login on a project's hosted login page,
 * this repository loads the developer's OAuth provider credentials from the database
 * and builds a ClientRegistration dynamically.
 *
 * Falls back to Arkil platform-level credentials for dashboard social login
 * (when no client context is present).
 */
@Component
@Slf4j
public class DynamicClientRegistrationRepository implements ClientRegistrationRepository {

    private final ProjectOAuthProviderRepository providerRepository;
    private final ProjectRepository projectRepository;
    private final ClientContextHolder clientContextHolder;
    private final SecretEncryptionService encryptionService;

    @Value("${arkil.oauth.google.client-id:}")
    private String googleClientId;
    @Value("${arkil.oauth.google.client-secret:}")
    private String googleClientSecret;
    @Value("${arkil.oauth.github.client-id:}")
    private String githubClientId;
    @Value("${arkil.oauth.github.client-secret:}")
    private String githubClientSecret;

    /**
     * Platform-level OAuth registrations (for Arkil dashboard social login).
     * Populated from application.properties on startup.
     */
    private static final Map<String, ClientRegistration> PLATFORM_REGISTRATIONS = new ConcurrentHashMap<>();

    public DynamicClientRegistrationRepository(
            ProjectOAuthProviderRepository providerRepository,
            ProjectRepository projectRepository,
            ClientContextHolder clientContextHolder,
            SecretEncryptionService encryptionService) {
        this.providerRepository = providerRepository;
        this.projectRepository = projectRepository;
        this.clientContextHolder = clientContextHolder;
        this.encryptionService = encryptionService;
    }

    @PostConstruct
    void bootstrapPlatformProviders() {
        if (googleClientId != null && !googleClientId.isBlank()) {
            PLATFORM_REGISTRATIONS.put("google", buildPlatformRegistration("google", googleClientId, googleClientSecret));
            log.info("Registered platform-level Google OAuth provider");
        }

        if (githubClientId != null && !githubClientId.isBlank()) {
            PLATFORM_REGISTRATIONS.put("github", buildPlatformRegistration("github", githubClientId, githubClientSecret));
            log.info("Registered platform-level GitHub OAuth provider");
        }

        log.info("Platform OAuth providers available: {}", PLATFORM_REGISTRATIONS.keySet());
    }

    /**
     * Returns the set of platform-level provider names that are configured.
     */
    public Set<String> getAvailablePlatformProviders() {
        return Collections.unmodifiableSet(PLATFORM_REGISTRATIONS.keySet());
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        // registrationId is the provider name: "google", "github", etc.

        // If there's a client context (end-user flow), resolve from project's provider config
        if (clientContextHolder.hasContext()) {
            ClientContext context = clientContextHolder.getContext();
            if (context.isResolved()) {
                ClientRegistration registration = resolveFromProject(context.getClientId(), registrationId);
                if (registration != null) {
                    return registration;
                }
            }
        }

        // Fallback: platform-level registration (for dashboard social login)
        return PLATFORM_REGISTRATIONS.get(registrationId);
    }

    /**
     * Register a platform-level OAuth client (e.g., Arkil's own Google app for developer signup).
     * Call this from a bootstrap config to set up platform credentials.
     */
    public static void registerPlatformClient(String registrationId, ClientRegistration registration) {
        PLATFORM_REGISTRATIONS.put(registrationId, registration);
    }

    private ClientRegistration buildPlatformRegistration(String provider, String clientId, String clientSecret) {
        ProviderDefaults defaults = PROVIDER_DEFAULTS.get(provider);
        var builder = ClientRegistration.withRegistrationId(provider)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(defaults.defaultScopes)
                .authorizationUri(defaults.authorizationUri)
                .tokenUri(defaults.tokenUri)
                .userInfoUri(defaults.userInfoUri)
                .userNameAttributeName(defaults.userNameAttribute)
                .clientName(defaults.displayName);

        if (defaults.jwkSetUri != null) {
            builder.jwkSetUri(defaults.jwkSetUri);
        }

        return builder.build();
    }

    private ClientRegistration resolveFromProject(String clientId, String provider) {
        // clientId is like "proj_my-app" — extract slug
        String slug = clientId.startsWith("proj_") ? clientId.substring(5) : clientId;

        Optional<Project> projectOpt = projectRepository.findBySlug(slug);
        if (projectOpt.isEmpty()) {
            log.debug("No project found for slug: {}", slug);
            return null;
        }

        Project project = projectOpt.get();
        Optional<ProjectOAuthProvider> providerOpt = providerRepository
                .findByProjectIdAndProviderAndEnvironment(project.getId(), provider, project.getEnvironment());

        if (providerOpt.isEmpty() || !providerOpt.get().isEnabled()) {
            log.debug("No enabled OAuth provider {} for project {}", provider, slug);
            return null;
        }

        ProjectOAuthProvider oauthProvider = providerOpt.get();
        return buildClientRegistration(provider, oauthProvider);
    }

    private ClientRegistration buildClientRegistration(String provider, ProjectOAuthProvider oauthProvider) {
        if ("custom-oidc".equalsIgnoreCase(provider)) {
            return buildCustomOidcRegistration(oauthProvider);
        }

        ProviderDefaults defaults = PROVIDER_DEFAULTS.get(provider.toLowerCase());
        if (defaults == null) {
            log.warn("No provider defaults for: {}", provider);
            return null;
        }

        String[] scopes = oauthProvider.getScopes() != null && !oauthProvider.getScopes().isBlank()
                ? oauthProvider.getScopes().split(",")
                : defaults.defaultScopes;

        var builder = ClientRegistration.withRegistrationId(provider)
                .clientId(oauthProvider.getClientId())
                .clientSecret(encryptionService.decrypt(oauthProvider.getClientSecretEncrypted()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(scopes)
                .authorizationUri(defaults.authorizationUri)
                .tokenUri(defaults.tokenUri)
                .userInfoUri(defaults.userInfoUri)
                .userNameAttributeName(defaults.userNameAttribute)
                .clientName(defaults.displayName);

        if (defaults.jwkSetUri != null) {
            builder.jwkSetUri(defaults.jwkSetUri);
        }

        return builder.build();
    }

    private ClientRegistration buildCustomOidcRegistration(ProjectOAuthProvider oauthProvider) {
        String[] scopes = oauthProvider.getScopes() != null && !oauthProvider.getScopes().isBlank()
                ? oauthProvider.getScopes().split(",")
                : new String[]{"openid", "profile", "email"};

        var builder = ClientRegistration.withRegistrationId("custom-oidc")
                .clientId(oauthProvider.getClientId())
                .clientSecret(encryptionService.decrypt(oauthProvider.getClientSecretEncrypted()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(scopes)
                .authorizationUri(oauthProvider.getAuthorizationUri())
                .tokenUri(oauthProvider.getTokenUri())
                .userInfoUri(oauthProvider.getUserInfoUri())
                .userNameAttributeName(oauthProvider.getUserNameAttribute())
                .clientName(oauthProvider.getDisplayName() != null && !oauthProvider.getDisplayName().isBlank()
                        ? oauthProvider.getDisplayName()
                        : "Enterprise SSO");

        if (oauthProvider.getJwkSetUri() != null && !oauthProvider.getJwkSetUri().isBlank()) {
            builder.jwkSetUri(oauthProvider.getJwkSetUri());
        }

        return builder.build();
    }

    // ─────────────────────────────────────────────────────────────────
    // Provider endpoint defaults
    // ─────────────────────────────────────────────────────────────────

    private record ProviderDefaults(
            String displayName,
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            String jwkSetUri,
            String userNameAttribute,
            String[] defaultScopes
    ) {}

    private static final Map<String, ProviderDefaults> PROVIDER_DEFAULTS = Map.of(
            "google", new ProviderDefaults(
                    "Google",
                    "https://accounts.google.com/o/oauth2/v2/auth",
                    "https://oauth2.googleapis.com/token",
                    "https://www.googleapis.com/oauth2/v3/userinfo",
                    "https://www.googleapis.com/oauth2/v3/certs",
                    "sub",
                    new String[]{"openid", "profile", "email"}
            ),
            "github", new ProviderDefaults(
                    "GitHub",
                    "https://github.com/login/oauth/authorize",
                    "https://github.com/login/oauth/access_token",
                    "https://api.github.com/user",
                    null, // GitHub doesn't use OIDC/JWKs
                    "id",
                    new String[]{"read:user", "user:email"}
            ),
            "apple", new ProviderDefaults(
                    "Apple",
                    "https://appleid.apple.com/auth/authorize",
                    "https://appleid.apple.com/auth/token",
                    "https://appleid.apple.com/auth/userinfo",
                    "https://appleid.apple.com/auth/keys",
                    "sub",
                    new String[]{"openid", "name", "email"}
            ),
            "linkedin", new ProviderDefaults(
                    "LinkedIn",
                    "https://www.linkedin.com/oauth/v2/authorization",
                    "https://www.linkedin.com/oauth/v2/accessToken",
                    "https://api.linkedin.com/v2/userinfo",
                    "https://www.linkedin.com/oauth/openid/jwks",
                    "sub",
                    new String[]{"openid", "profile", "email"}
            )
    );
}
