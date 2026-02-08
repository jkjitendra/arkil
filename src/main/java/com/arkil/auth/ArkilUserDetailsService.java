package com.arkil.auth;

import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Custom UserDetailsService that loads users with their password credentials.
 * For Phase 1, we use a simplified lookup (username globally unique for demo).
 * Phase 3 will add tenant-aware context resolution.
 */
@Service
@RequiredArgsConstructor
public class ArkilUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // For Phase 1 demo: find user by username (simplified, no tenant context yet)
        ArkilUser user = userRepository.findAll().stream()
                .filter(u -> u.getUsername().equals(username) || u.getEmail().equals(username))
                .findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        PasswordCredential passwordCredential = passwordCredentialRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException("No password set for user: " + username));

        Collection<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .collect(Collectors.toSet());

        return User.builder()
                .username(user.getUsername())
                .password(passwordCredential.getPasswordHash())
                .disabled(!user.getEnabled())
                .authorities(authorities)
                .build();
    }
}
