package com.arkil.config;

import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.project.ProjectService;
import com.arkil.tenant.Tenant;
import com.arkil.tenant.TenantRepository;
import com.arkil.user.ArkilUser;
import com.arkil.user.Role;
import com.arkil.user.RoleRepository;
import com.arkil.user.UserRepository;
import com.arkil.webhook.Webhook;
import com.arkil.webhook.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bootstrap demo data for development and testing.
 * Creates:
 * - Global roles (USER, TENANT_ADMIN)
 * - A demo tenant with admin + regular user
 * - A demo project with API keys and localhost redirect URIs
 * - A sample webhook for the demo project
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
    private final ProjectService projectService;
    private final WebhookRepository webhookRepository;

    @Value("${arkil.admin.bootstrap.username:}")
    private String adminUsername;

    @Value("${arkil.admin.bootstrap.password:}")
    private String adminPassword;

    @Value("${arkil.admin.bootstrap.email:admin@arkil.local}")
    private String adminEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (tenantRepository.count() > 0) {
            log.info("Demo data already exists, skipping bootstrap");
            return;
        }

        log.info("Creating demo data...");

        // ── Roles ────────────────────────────────────────────────────
        Role userRole = roleRepository.save(Role.builder()
                .name("USER")
                .description("Standard user role")
                .build());

        Role tenantAdminRole = roleRepository.save(Role.builder()
                .name("TENANT_ADMIN")
                .description("Tenant administrator - manages projects and auth configuration")
                .build());

        // ── Demo Tenant ──────────────────────────────────────────────
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .slug("demo")
                .name("Demo Organization")
                .enabled(true)
                .build());

        // ── Admin User (TENANT_ADMIN) ────────────────────────────────
        ArkilUser admin = userRepository.save(ArkilUser.builder()
                .tenant(tenant)
                .username(adminUsername)
                .email(adminEmail)
                .displayName("Demo Admin")
                .enabled(true)
                .emailVerified(true)
                .build());
        admin.getRoles().add(tenantAdminRole);
        admin.getRoles().add(userRole);
        userRepository.save(admin);

        passwordCredentialRepository.save(PasswordCredential.builder()
                .user(admin)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .algorithm("bcrypt")
                .build());

        // ── Regular End-User ─────────────────────────────────────────
        ArkilUser endUser = userRepository.save(ArkilUser.builder()
                .tenant(tenant)
                .username("demo")
                .email("demo@arkil.local")
                .displayName("Demo User")
                .enabled(true)
                .emailVerified(true)
                .build());
        endUser.getRoles().add(userRole);
        userRepository.save(endUser);

        passwordCredentialRepository.save(PasswordCredential.builder()
                .user(endUser)
                .passwordHash(passwordEncoder.encode("password"))
                .algorithm("bcrypt")
                .build());

        // ── Demo Project ─────────────────────────────────────────────
        try {
            ProjectService.ProjectWithKeys result = projectService.createProject(
                    new ProjectService.CreateProjectRequest(
                            "Demo App",
                            "demo-app",
                            "A demo application to explore Arkil features",
                            null, // DEVELOPMENT
                            List.of("http://localhost:3000", "http://localhost:5173"),
                            List.of("http://localhost:3000/callback", "http://localhost:5173/callback",
                                    "http://localhost:8080/login/oauth2/code/google",
                                    "http://localhost:8080/login/oauth2/code/github")
                    ),
                    admin.getId(),
                    tenant.getId());

            log.info("Demo project created: slug={}, client_id=proj_demo-app", result.project().getSlug());
            log.info("Demo API key: pk_{}", result.apiKey().getPublishableKey());

            // ── Sample Webhook ───────────────────────────────────────
            try {
                webhookRepository.save(Webhook.builder()
                        .projectId(result.project().getId())
                        .url("https://webhook.site/demo")
                        .secret("whsec_demo_signing_secret_for_development_only")
                        .events("user.created,session.created,password.changed")
                        .description("Sample webhook (replace URL with your endpoint)")
                        .enabled(false) // Disabled by default — just for demo UI
                        .build());
                log.info("Sample webhook created for demo project");
            } catch (Exception e) {
                log.warn("Could not create sample webhook: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.warn("Could not create demo project (may already exist): {}", e.getMessage());
        }

        log.info("─────────────────────────────────────────────────────");
        log.info("Demo data created successfully!");
        log.info("  Admin login:  {}  /  {}", adminEmail, adminPassword);
        log.info("  User login:   demo@arkil.local  /  password");
        log.info("  Project:      Demo App (client_id=proj_demo-app)");
        log.info("─────────────────────────────────────────────────────");
    }
}

