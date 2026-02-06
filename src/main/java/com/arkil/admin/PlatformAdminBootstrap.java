package com.arkil.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstrap runner that creates the initial platform admin if none exists.
 * Credentials are read from environment/config on first run only.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformAdminBootstrap implements ApplicationRunner {

    private final PlatformAdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${arkil.admin.bootstrap.username:admin}")
    private String bootstrapUsername;

    @Value("${arkil.admin.bootstrap.password:}")
    private String bootstrapPassword;

    @Value("${arkil.admin.bootstrap.email:admin@arkil.local}")
    private String bootstrapEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            log.info("Platform admin(s) already exist, skipping bootstrap");
            return;
        }

        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.warn("No bootstrap password configured (arkil.admin.bootstrap.password). "
                    + "Set this property to create the initial admin.");
            return;
        }

        PlatformAdmin admin = PlatformAdmin.builder()
                .username(bootstrapUsername)
                .passwordHash(passwordEncoder.encode(bootstrapPassword))
                .email(bootstrapEmail)
                .enabled(true)
                .build();

        adminRepository.save(admin);
        log.info("Created bootstrap platform admin: {}", bootstrapUsername);
    }
}
