package com.arkil.config;

import com.arkil.audit.ActorType;
import com.arkil.audit.AuditEventType;
import com.arkil.audit.AuditService;
import com.arkil.client.AuthModule;
import com.arkil.credential.social.OAuth2IdentityService;
import com.arkil.policy.ClientContext;
import com.arkil.policy.ClientContextHolder;
import com.arkil.user.ArkilUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Map;
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
            String tenantSlug = null;
            if (clientContextHolder.hasContext() && clientContextHolder.getContext().isResolved()) {
                // End-user social login — resolve tenant from client context
                // Will be fully implemented in Phase 4 with TenantContextFilter
                tenantSlug = null; // TODO Phase 4: resolve tenant slug from project's tenant
            }
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

    @Bean
    public AuthenticationSuccessHandler oauth2SuccessHandler() {
        return (request, response, authentication) -> {
            String username = authentication.getName();

            if (clientContextHolder.hasContext()) {
                ClientContext context = clientContextHolder.getContext();
                auditService.logSuccess(AuditEventType.AUTH_LOGIN_SUCCESS, username, ActorType.USER,
                        context.getClientId(), request);
            }

            log.info("OAuth2 login successful for user: {}", username);
            response.sendRedirect("/");
        };
    }

    @Bean
    public AuthenticationFailureHandler oauth2FailureHandler() {
        return (request, response, exception) -> {
            String clientId = clientContextHolder.hasContext() ?
                    clientContextHolder.getContext().getClientId() : "unknown";

            auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, "oauth2",
                    ActorType.USER, clientId, exception.getMessage(), request);

            log.warn("OAuth2 login failed: {}", exception.getMessage());
            response.sendRedirect("/login?error=oauth2");
        };
    }

    private AuthModule getModuleForProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "google" -> AuthModule.OAUTH2_GOOGLE;
            case "github" -> AuthModule.OAUTH2_GITHUB;
            case "apple" -> AuthModule.OAUTH2_APPLE;
            case "linkedin" -> AuthModule.OAUTH2_LINKEDIN;
            default -> null;
        };
    }
}
