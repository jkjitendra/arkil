package com.arkil.auth;

import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.email.EmailTokenService;
import com.arkil.project.Project;
import com.arkil.project.ProjectRepository;
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

import java.util.UUID;

/**
 * Service for end-user registration within a project's tenant.
 * Unlike RegistrationService (developer signup), this creates users
 * scoped to a specific project's tenant with the USER role.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EndUserRegistrationService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final RoleRepository roleRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailTokenService emailTokenService;

    /**
     * Register an end-user for a specific project's tenant.
     *
     * @param email     User email
     * @param password  User password
     * @param clientId  The OAuth2 client_id (e.g., "proj_my-app")
     * @return The created user
     */
    @Transactional
    public ArkilUser registerEndUser(String email, String password, String clientId) {
        // Resolve tenant from client_id
        UUID tenantId = resolvetenantId(clientId);

        // Check uniqueness within tenant
        if (userRepository.findByTenantIdAndEmail(tenantId, email).isPresent()) {
            throw new EndUserRegistrationException("An account with this email already exists");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EndUserRegistrationException("Tenant not found"));

        // Find or create USER role
        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("USER")
                        .description("Standard user role")
                        .build()));

        // Create user
        String username = email.split("@")[0] + "_" + System.currentTimeMillis() % 10000;
        ArkilUser user = ArkilUser.builder()
                .tenant(tenant)
                .username(username)
                .email(email)
                .enabled(true)
                .emailVerified(false)
                .build();

        user.getRoles().add(userRole);
        user = userRepository.save(user);

        // Create password credential
        passwordCredentialRepository.save(PasswordCredential.builder()
                .user(user)
                .passwordHash(passwordEncoder.encode(password))
                .algorithm("bcrypt")
                .build());

        // Send verification email
        emailTokenService.sendVerificationEmail(user.getId());

        log.info("End-user registered: email={}, tenant={}, clientId={}", email, tenant.getSlug(), clientId);
        return user;
    }

    private UUID resolvetenantId(String clientId) {
        if (clientId != null && clientId.startsWith("proj_")) {
            String slug = clientId.substring("proj_".length());
            return projectRepository.findBySlug(slug)
                    .map(Project::getTenantId)
                    .orElseThrow(() -> new EndUserRegistrationException("Project not found"));
        }
        throw new EndUserRegistrationException("Invalid client context for registration");
    }

    public static class EndUserRegistrationException extends RuntimeException {
        public EndUserRegistrationException(String message) {
            super(message);
        }
    }
}
