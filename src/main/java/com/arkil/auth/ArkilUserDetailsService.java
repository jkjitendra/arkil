package com.arkil.auth;

import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
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
import java.util.stream.Collectors;

/**
 * Custom UserDetailsService that loads users with their password credentials.
 * Supports login by email or username.
 * Uses the user's UUID as the Spring Security principal (username field)
 * so that JWT sub claim contains the UUID for downstream use.
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
        // Try email first, then username
        Optional<ArkilUser> userOpt = userRepository.findByEmail(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUsername(identifier);
        }

        ArkilUser user = userOpt
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));

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
}
