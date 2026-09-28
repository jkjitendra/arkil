package com.arkil.policy;

import com.arkil.audit.ActorType;
import com.arkil.audit.AuditEventType;
import com.arkil.audit.AuditService;
import com.arkil.client.AuthModule;
import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.credential.totp.TotpService;
import com.arkil.project.Project;
import com.arkil.project.ProjectRepository;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Policy-aware authentication provider that enforces module policies.
 * If EMAIL_PASSWORD is disabled for the client, authentication is rejected.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyAwareAuthenticationProvider implements AuthenticationProvider {

    private final ClientContextHolder contextHolder;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final ClientContextResolver clientContextResolver;
    private final ProjectRepository projectRepository;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();
        HttpServletRequest request = getCurrentRequest();

        // Get client context if available (optional for OAuth2 auth server flows)
        ClientContext context = contextHolder.hasContext() ? contextHolder.getContext() : null;
        if (context == null && request != null) {
            ClientContext resolvedContext = clientContextResolver.resolve(request);
            if (resolvedContext.isResolved()) {
                contextHolder.setContext(resolvedContext);
                context = resolvedContext;
            }
        }

        // If we have a client context, enforce module policies
        if (context != null && context.isResolved()) {
            if (!context.isModuleEnabled(AuthModule.EMAIL_PASSWORD)) {
                log.warn("Password auth blocked for client {} - EMAIL_PASSWORD disabled", context.getClientId());
                auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, username, ActorType.USER,
                        context.getClientId(), "EMAIL_PASSWORD module disabled", request);
                throw new BadCredentialsException("Password authentication is not enabled for this client");
            }
        }

        // Authenticate the user
        String clientId = context != null ? context.getClientId() : "auth-server";
        try {
            ArkilUser user = resolveUser(username, context, request);

            if (!user.getEnabled()) {
                throw new DisabledException("User account is disabled");
            }

            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, username, ActorType.USER,
                        clientId, "Email not verified", request);
                throw new EmailNotVerifiedAuthenticationException(user.getEmail());
            }

            PasswordCredential credential = passwordCredentialRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

            if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
                auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, username, ActorType.USER,
                        clientId, "Invalid password", request);
                throw new BadCredentialsException("Invalid username or password");
            }

            if (totpService.isEnabled(user.getId())) {
                String totpCode = request != null ? request.getParameter("totpCode") : null;
                if (totpCode == null || totpCode.isBlank()) {
                    auditService.logFailure(AuditEventType.MFA_FAILED, username, ActorType.USER,
                            clientId, "TOTP code required", request);
                    throw new TotpRequiredAuthenticationException(username);
                }

                if (!totpService.verify(user.getId(), totpCode)) {
                    throw new InvalidTotpAuthenticationException(username);
                }
            }

            Collection<GrantedAuthority> authorities = user.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                    .collect(Collectors.toSet());

            auditService.logSuccess(AuditEventType.AUTH_LOGIN_SUCCESS, username, ActorType.USER,
                    clientId, request);

            // Update last login timestamp
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            // Use UUID as principal to match ArkilUserDetailsService — this becomes JWT sub
            return new UsernamePasswordAuthenticationToken(user.getId().toString(), null, authorities);

        } catch (AuthenticationException e) {
            if (!(e instanceof BadCredentialsException)) {
                auditService.logFailure(AuditEventType.AUTH_LOGIN_FAILURE, username, ActorType.USER,
                        clientId, e.getMessage(), request);
            }
            throw e;
        }
    }

    private ArkilUser resolveUser(String identifier, ClientContext context, HttpServletRequest request) {
        if (context != null && context.isResolved()) {
            String clientId = context.getClientId();
            if (clientId == null || !clientId.startsWith("proj_")) {
                throw new BadCredentialsException("Invalid application context");
            }
            Project project = projectRepository.findBySlug(clientId.substring("proj_".length()))
                    .filter(p -> p.isActive() && p.getDeletedAt() == null && p.getTenantId() != null)
                    .orElseThrow(() -> new BadCredentialsException("Invalid application context"));
            return userRepository.findByTenantIdAndEmail(project.getTenantId(), identifier)
                    .or(() -> userRepository.findByTenantIdAndUsername(project.getTenantId(), identifier))
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        }

        String requestedClient = request == null ? null : request.getParameter("client_id");
        if (requestedClient != null && !requestedClient.isBlank()
                && !"arkil-dashboard".equals(requestedClient)) {
            throw new BadCredentialsException("Invalid application context");
        }

        // A context-free hosted login is for the developer dashboard only.
        // End users with the same email in other tenants cannot be selected.
        return java.util.stream.Stream.concat(
                        userRepository.findAllByEmail(identifier).stream(),
                        userRepository.findAllByUsername(identifier).stream())
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> "TENANT_ADMIN".equals(role.getName())))
                .findFirst()
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
