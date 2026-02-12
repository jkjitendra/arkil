package com.arkil.auth;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Service for developer registration on the Arkil platform.
 * When a developer signs up, a Tenant is auto-provisioned and
 * the user is assigned the TENANT_ADMIN role.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern SLUG_PATTERN = Pattern.compile("[^a-z0-9-]");

    @Transactional
    public ArkilUser registerDeveloper(String email, String password, String orgName, String displayName) {
        // Validate email uniqueness globally (developers are platform-level accounts)
        if (userRepository.existsByEmail(email)) {
            throw new RegistrationException("An account with this email already exists");
        }

        // Create tenant
        String slug = generateSlug(orgName);
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .slug(slug)
                .name(orgName)
                .enabled(true)
                .build());

        // Find or create TENANT_ADMIN role
        Role tenantAdminRole = roleRepository.findByName("TENANT_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("TENANT_ADMIN")
                        .description("Tenant administrator - manages projects and auth configuration")
                        .build()));

        // Create user with TENANT_ADMIN role
        String username = email.split("@")[0];
        ArkilUser user = ArkilUser.builder()
                .tenant(tenant)
                .username(username)
                .email(email)
                .displayName(displayName)
                .enabled(true)
                .emailVerified(false)
                .build();

        user.getRoles().add(tenantAdminRole);
        user = userRepository.save(user);

        // Create password credential
        passwordCredentialRepository.save(PasswordCredential.builder()
                .user(user)
                .passwordHash(passwordEncoder.encode(password))
                .algorithm("bcrypt")
                .build());

        log.info("Developer registered: email={}, tenant={}, userId={}", email, slug, user.getId());
        return user;
    }

    private String generateSlug(String orgName) {
        // Normalize and slugify the org name
        String normalized = Normalizer.normalize(orgName, Normalizer.Form.NFD);
        String slug = SLUG_PATTERN.matcher(normalized.toLowerCase(Locale.ROOT).trim().replace(' ', '-'))
                .replaceAll("");

        if (slug.isEmpty()) {
            slug = "org";
        }

        // Ensure uniqueness
        String baseSlug = slug;
        int counter = 1;
        while (tenantRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }

    public static class RegistrationException extends RuntimeException {
        public RegistrationException(String message) {
            super(message);
        }
    }
}
