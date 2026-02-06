package com.arkil.config;

import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.tenant.Tenant;
import com.arkil.tenant.TenantRepository;
import com.arkil.user.ArkilUser;
import com.arkil.user.Role;
import com.arkil.user.RoleRepository;
import com.arkil.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstrap demo data for Phase 1 testing.
 * Creates a demo tenant, user, and role.
 */
@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class DemoDataBootstrap implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (tenantRepository.count() > 0) {
            log.info("Demo data already exists, skipping bootstrap");
            return;
        }

        log.info("Creating demo data for Phase 1 testing...");

        // Create demo tenant
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .slug("demo")
                .name("Demo Organization")
                .enabled(true)
                .build());

        // Create USER role
        Role userRole = roleRepository.save(Role.builder()
                .name("USER")
                .description("Standard user role")
                .build());

        // Create demo user
        ArkilUser user = userRepository.save(ArkilUser.builder()
                .tenant(tenant)
                .username("demo")
                .email("demo@arkil.local")
                .displayName("Demo User")
                .enabled(true)
                .emailVerified(true)
                .build());

        user.getRoles().add(userRole);
        userRepository.save(user);

        // Create password credential
        passwordCredentialRepository.save(PasswordCredential.builder()
                .user(user)
                .passwordHash(passwordEncoder.encode("demo123"))
                .algorithm("bcrypt")
                .build());

        log.info("Demo data created: tenant={}, user={}", tenant.getSlug(), user.getUsername());
        log.info("Login with: username=demo, password=demo123");
    }
}
