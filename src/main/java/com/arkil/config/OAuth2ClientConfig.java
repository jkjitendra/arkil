package com.arkil.config;

import com.arkil.auth.AuthSessionAttributes;
import com.arkil.audit.ActorType;
import com.arkil.audit.AuditEventType;
import com.arkil.audit.AuditService;
import com.arkil.audit.ProjectWebhookEventService;
import com.arkil.client.AuthModule;
import com.arkil.credential.social.OAuth2IdentityService;
import com.arkil.policy.ClientContext;
import com.arkil.policy.ClientContextHolder;
import com.arkil.project.Project;
import com.arkil.project.ProjectRepository;
import com.arkil.tenant.Tenant;
import com.arkil.tenant.TenantRepository;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OAuth2 client configuration for social login.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OAuth2ClientConfig {

    private final OAuth2IdentityService oAuth2IdentityService;
    private final ClientContextHolder clientContextHolder;
    private final AuditService auditService;
    private final ProjectRepository projectRepository;
    private final TenantRepository tenantRepository;
    private final ProjectWebhookEventService projectWebhookEventService;
    private final UserRepository userRepository;

    /**
     * Custom OAuth2UserService that links external identities to ArkilUsers.
     */
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

        return userRequest -> {
            String provider = userRequest.getClientRegistration().getRegistrationId();

            // Check if this provider is enabled for the client
            if (clientContextHolder.hasContext()) {
                ClientContext context = clientContextHolder.getContext();
                AuthModule requiredModule = getModuleForProvider(provider);

                if (requiredModule != null && !context.isModuleEnabled(requiredModule)) {
                    log.warn("OAuth2 provider {} is disabled for client {}", provider, context.getClientId());
                    throw new OAuth2AuthenticationException(new OAuth2Error("provider_disabled",
                            "OAuth2 provider " + provider + " is not enabled for this client", null));
                }
            }

            // Load user from OAuth2 provider
            OAuth2User oauth2User = delegate.loadUser(userRequest);

            // Link to ArkilUser
            // Pass null for dashboard social login (auto-provisions tenant),
            // or tenant slug from client context for end-user social login
            String tenantSlug = resolveTenantSlugFromClientContext();
            ArkilUser arkilUser = oAuth2IdentityService.processOAuth2Login(
                    provider, oauth2User, tenantSlug);

            // Return enriched OAuth2User with ArkilUser authorities
            var authorities = arkilUser.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                    .collect(Collectors.toSet());

            return new DefaultOAuth2User(
                    authorities,
                    Map.of(
                            "sub", arkilUser.getId().toString(),
                            "username", arkilUser.getUsername(),
                            "email", arkilUser.getEmail(),
                            "name", arkilUser.getDisplayName() != null ? arkilUser.getDisplayName() : arkilUser.getUsername()
                    ),
                    "username"
            );
        };
    }

    /**
     * Custom OidcUserService for OIDC providers (Google, Apple, LinkedIn).
     * These providers return ID tokens, unlike GitHub which only returns OAuth2 tokens.
     */
    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();

        return userRequest -> {
            String provider = userRequest.getClientRegistration().getRegistrationId();

            // Check if this provider is enabled for the client
            if (clientContextHolder.hasContext()) {
                ClientContext context = clientContextHolder.getContext();
                AuthModule requiredModule = getModuleForProvider(provider);

                if (requiredModule != null && !context.isModuleEnabled(requiredModule)) {
                    log.warn("OAuth2 provider {} is disabled for client {}", provider, context.getClientId());
                    throw new OAuth2AuthenticationException(new OAuth2Error("provider_disabled",
                            "OAuth2 provider " + provider + " is not enabled for this client", null));
                }
            }

            // Load OIDC user from provider
            OidcUser oidcUser = delegate.loadUser(userRequest);

            // Link to ArkilUser
            String tenantSlug = resolveTenantSlugFromClientContext();
            ArkilUser arkilUser = oAuth2IdentityService.processOAuth2Login(
                    provider, oidcUser, tenantSlug);

            // Return enriched OidcUser with ArkilUser authorities
            var authorities = arkilUser.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                    .collect(Collectors.toSet());

            return new DefaultOidcUser(
                    authorities,
                    oidcUser.getIdToken(),
                    oidcUser.getUserInfo(),
                    "sub"
            );
        };
    }

    @Bean
    public AuthenticationSuccessHandler oauth2SuccessHandler() {
        // Use SavedRequestAwareAuthenticationSuccessHandler so that after social login,
        // the user is redirected back to the original OIDC /oauth2/authorize request
        // (which was saved before the login redirect). This completes the OIDC flow
        // and issues tokens to the dashboard SPA.
        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");

        return (request, response, authentication) -> {
            String username = authentication.getName();
            HttpSession session = request.getSession(false);
            String returnTo = session != null
                    ? (String) session.getAttribute(AuthSessionAttributes.SOCIAL_LOGIN_RETURN_TO)
                    : null;

            if (clientContextHolder.hasContext()) {
                ClientContext context = clientContextHolder.getContext();
                auditService.logSuccess(AuditEventType.AUTH_LOGIN_SUCCESS, username, ActorType.USER,
                        context.getClientId(), request);

                resolveAuthenticatedUser(authentication).ifPresent(user ->
                        projectWebhookEventService.sessionCreated(
                                user,
                                ActorType.USER,
                                user.getId().toString(),
                                request,
                                context.getClientId(),
                                user.getTenant() != null ? user.getTenant().getId() : null,
                                "social"
                        ));
            }

            log.info("OAuth2 login successful for user: {}", username);
            if (returnTo != null) {
                session.removeAttribute(AuthSessionAttributes.SOCIAL_LOGIN_RETURN_TO);
                response.sendRedirect(returnTo);
                return;
            }
            handler.onAuthenticationSuccess(request, response, authentication);
        };
    }

    private Optional<ArkilUser> resolveAuthenticatedUser(org.springframework.security.core.Authentication authentication) {
        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            Object sub = oauth2User.getAttributes().get("sub");
            if (sub instanceof String subject) {
                try {
                    return userRepository.findById(UUID.fromString(subject));
                } catch (IllegalArgumentException ignored) {
                }
            }

            Object email = oauth2User.getAttributes().get("email");
            if (email instanceof String emailAddress) {
                return userRepository.findByEmail(emailAddress);
            }
        }

        return Optional.empty();
    }

    @Bean
    public AuthenticationFailureHandler oauth2FailureHandler() {
        return (request, response, exception) -> {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(AuthSessionAttributes.SOCIAL_LOGIN_RETURN_TO);
            }

            String clientId = clientContextHolder.hasContext() ?
                    clientContextHolder.getContext().getClientId() : "unknown";

            auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, "oauth2",
                    ActorType.USER, clientId, exception.getMessage(), request);

            log.warn("OAuth2 login failed: {}", exception.getMessage());
            response.sendRedirect("/login?error=oauth2");
        };
    }

    /**
     * Resolve the tenant slug from the current client context.
     * Returns null for dashboard social login (no client context → auto-provision tenant).
     * Returns the project's tenant slug for end-user social login.
     */
    private String resolveTenantSlugFromClientContext() {
        if (!clientContextHolder.hasContext() || !clientContextHolder.getContext().isResolved()) {
            return null; // Dashboard social login — will auto-provision tenant
        }

        String clientId = clientContextHolder.getContext().getClientId();
        if (clientId == null || !clientId.startsWith("proj_")) {
            return null;
        }

        String slug = clientId.substring("proj_".length());
        Optional<Project> projectOpt = projectRepository.findBySlug(slug);
        if (projectOpt.isEmpty() || projectOpt.get().getTenantId() == null) {
            log.warn("Could not resolve tenant for project client_id: {}", clientId);
            return null;
        }

        UUID tenantId = projectOpt.get().getTenantId();
        return tenantRepository.findById(tenantId)
                .map(Tenant::getSlug)
                .orElse(null);
    }

    private AuthModule getModuleForProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "google" -> AuthModule.OAUTH2_GOOGLE;
            case "github" -> AuthModule.OAUTH2_GITHUB;
            case "apple" -> AuthModule.OAUTH2_APPLE;
            case "linkedin" -> AuthModule.OAUTH2_LINKEDIN;
            case "custom-oidc" -> AuthModule.OAUTH2_CUSTOM_OIDC;
            default -> null;
        };
    }
}
