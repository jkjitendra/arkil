package com.arkil.auth;

import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.tenant.TenantContext;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Custom UserDetailsService that loads users with their password credentials.
 * Supports login by email or username.
 * Uses the user's UUID as the Spring Security principal (username field)
 * so that JWT sub claim contains the UUID for downstream use.
 *
 * Tenant-aware: When TenantContext is set (end-user login via client context),
 * user lookup is scoped to that tenant. When no tenant context (dashboard login),
 * global email/username lookup is used.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArkilUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        UUID tenantId = TenantContext.getTenantId();
        ArkilUser user;

        if (tenantId != null) {
            // End-user login context: scope lookup to tenant
            user = findUserInTenant(identifier, tenantId);
        } else {
            // Dashboard login context: global lookup
            user = findUserGlobally(identifier);
        }

        PasswordCredential passwordCredential = passwordCredentialRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException("No password set for user: " + identifier));

        Collection<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .collect(Collectors.toSet());

        // Use user UUID as the principal username — this becomes the JWT sub claim
        return User.builder()
                .username(user.getId().toString())
                .password(passwordCredential.getPasswordHash())
                .disabled(!user.getEnabled())
                .authorities(authorities)
                .build();
    }

    private ArkilUser findUserInTenant(String identifier, UUID tenantId) {
        Optional<ArkilUser> userOpt = userRepository.findByTenantIdAndEmail(tenantId, identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByTenantIdAndUsername(tenantId, identifier);
        }

        return userOpt.orElseThrow(() ->
                new UsernameNotFoundException("User not found in tenant: " + identifier));
    }

    private ArkilUser findUserGlobally(String identifier) {
        Optional<ArkilUser> userOpt = userRepository.findByEmail(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUsername(identifier);
        }

        return userOpt.orElseThrow(() ->
                new UsernameNotFoundException("User not found: " + identifier));
    }
}
