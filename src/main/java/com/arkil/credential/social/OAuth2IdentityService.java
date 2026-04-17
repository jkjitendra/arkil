package com.arkil.credential.social;

import com.arkil.audit.ActorType;
import com.arkil.audit.ProjectWebhookEventService;
import com.arkil.tenant.Tenant;
import com.arkil.tenant.TenantRepository;
import com.arkil.user.ArkilUser;
import com.arkil.user.Role;
import com.arkil.user.RoleRepository;
import com.arkil.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service for linking OAuth2 identities to ArkilUser accounts.
 * Handles first-time login (create user) and returning login (link existing).
 *
 * When no tenant context is provided (dashboard social login), a new tenant
 * is auto-provisioned and the user becomes TENANT_ADMIN.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2IdentityService {

    private final SocialIdentityRepository socialIdentityRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final ProjectWebhookEventService projectWebhookEventService;

    private static final Pattern SLUG_PATTERN = Pattern.compile("[^a-z0-9-]");

    /**
     * Process an OAuth2 login and return/create the associated ArkilUser.
     *
     * @param provider   The OAuth2 provider name (google, github, etc.)
     * @param oauth2User The authenticated OAuth2 user
     * @param tenantSlug The tenant slug, or null for dashboard social login (auto-provision)
     * @return The linked or newly created ArkilUser
     */
    @Transactional
    public ArkilUser processOAuth2Login(String provider, OAuth2User oauth2User, String tenantSlug) {
        String subjectId = extractSubjectId(provider, oauth2User);
        String email = extractEmail(provider, oauth2User);
        String displayName = extractDisplayName(provider, oauth2User);
        String pictureUrl = extractPictureUrl(provider, oauth2User);

        log.info("Processing OAuth2 login: provider={}, subject={}, email={}", provider, subjectId, email);

        // Check if this social identity already exists
        Optional<SocialIdentity> existingIdentity = socialIdentityRepository
                .findByProviderAndProviderSubjectId(provider, subjectId);

        if (existingIdentity.isPresent()) {
            // Returning user - update last used and return
            SocialIdentity identity = existingIdentity.get();
            identity.setLastUsedAt(Instant.now());
            identity.setProviderEmail(email);
            identity.setProviderDisplayName(displayName);
            identity.setPictureUrl(pictureUrl);
            socialIdentityRepository.save(identity);

            // Update last login timestamp
            ArkilUser returningUser = identity.getUser();
            returningUser.setLastLoginAt(Instant.now());
            userRepository.save(returningUser);

            log.info("Returning OAuth2 user: {}", returningUser.getUsername());
            return returningUser;
        }

        // New social login - determine tenant
        Tenant tenant;
        boolean isDeveloperSignup;

        if (tenantSlug == null) {
            // Dashboard social login — check if user exists by email globally
            Optional<ArkilUser> existingUser = userRepository.findByEmail(email);
            if (existingUser.isPresent()) {
                // Link social identity to existing user
                ArkilUser user = existingUser.get();
                linkSocialIdentity(user, provider, subjectId, email, displayName, pictureUrl);
                user.setLastLoginAt(Instant.now());
                userRepository.save(user);
                return user;
            }

            // Auto-provision new tenant for developer signup via social login
            tenant = autoProvisionTenant(email, displayName);
            isDeveloperSignup = true;
        } else {
            tenant = tenantRepository.findBySlug(tenantSlug)
                    .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantSlug));
            isDeveloperSignup = false;
        }

        // Try to find existing user by email in this tenant
        boolean[] created = {false};
        ArkilUser user = userRepository.findByTenantIdAndEmail(tenant.getId(), email)
                .orElseGet(() -> {
                    created[0] = true;
                    return createNewUser(tenant, email, displayName, isDeveloperSignup);
                });

        // Link the social identity
        linkSocialIdentity(user, provider, subjectId, email, displayName, pictureUrl);

        if (created[0] && !isDeveloperSignup) {
            projectWebhookEventService.userCreated(
                    user,
                    ActorType.USER,
                    user.getId().toString(),
                    null,
                    null,
                    tenant.getId(),
                    "social_login"
            );
        }

        return user;
    }

    private void linkSocialIdentity(ArkilUser user, String provider, String subjectId,
                                     String email, String displayName, String pictureUrl) {
        SocialIdentity socialIdentity = SocialIdentity.builder()
                .user(user)
                .provider(provider)
                .providerSubjectId(subjectId)
                .providerEmail(email)
                .providerDisplayName(displayName)
                .pictureUrl(pictureUrl)
                .build();

        socialIdentityRepository.save(socialIdentity);
        log.info("Created new social identity for user: {} with provider: {}", user.getUsername(), provider);
    }

    private Tenant autoProvisionTenant(String email, String displayName) {
        String orgName = displayName != null && !displayName.isBlank()
                ? displayName + "'s Organization"
                : email.split("@")[0] + "'s Organization";

        String slug = generateSlug(orgName);

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .slug(slug)
                .name(orgName)
                .enabled(true)
                .build());

        log.info("Auto-provisioned tenant for social signup: slug={}", slug);
        return tenant;
    }

    private ArkilUser createNewUser(Tenant tenant, String email, String displayName, boolean isDeveloperSignup) {
        // Generate username from email
        String username = email.split("@")[0] + "_" + System.currentTimeMillis() % 10000;

        String roleName = isDeveloperSignup ? "TENANT_ADMIN" : "USER";
        String roleDescription = isDeveloperSignup
                ? "Tenant administrator - manages projects and auth configuration"
                : "Standard user role";

        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name(roleName)
                        .description(roleDescription)
                        .build()));

        ArkilUser user = ArkilUser.builder()
                .tenant(tenant)
                .username(username)
                .email(email)
                .displayName(displayName)
                .enabled(true)
                .emailVerified(true) // Trust the OAuth2 provider's email verification
                .build();

        user.getRoles().add(role);
        user = userRepository.save(user);

        log.info("Created new user via OAuth2: {} (role={})", user.getUsername(), roleName);
        return user;
    }

    private String generateSlug(String orgName) {
        String normalized = Normalizer.normalize(orgName, Normalizer.Form.NFD);
        String slug = SLUG_PATTERN.matcher(normalized.toLowerCase(Locale.ROOT).trim().replace(' ', '-'))
                .replaceAll("");

        if (slug.isEmpty()) {
            slug = "org";
        }

        String baseSlug = slug;
        int counter = 1;
        while (tenantRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }

    private String extractSubjectId(String provider, OAuth2User oauth2User) {
        return switch (provider.toLowerCase()) {
            case "google" -> oauth2User.getAttribute("sub");
            case "github" -> String.valueOf(oauth2User.getAttribute("id"));
            case "apple" -> oauth2User.getAttribute("sub");
            case "linkedin" -> oauth2User.getAttribute("sub");
            default -> oauth2User.getName();
        };
    }

    private String extractEmail(String provider, OAuth2User oauth2User) {
        return oauth2User.getAttribute("email");
    }

    private String extractDisplayName(String provider, OAuth2User oauth2User) {
        return switch (provider.toLowerCase()) {
            case "google" -> oauth2User.getAttribute("name");
            case "github" -> {
                String name = oauth2User.getAttribute("name");
                yield name != null ? name : oauth2User.getAttribute("login");
            }
            case "apple" -> {
                String firstName = oauth2User.getAttribute("firstName");
                String lastName = oauth2User.getAttribute("lastName");
                yield (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
            }
            default -> oauth2User.getAttribute("name");
        };
    }

    private String extractPictureUrl(String provider, OAuth2User oauth2User) {
        return switch (provider.toLowerCase()) {
            case "google" -> oauth2User.getAttribute("picture");
            case "github" -> oauth2User.getAttribute("avatar_url");
            default -> null;
        };
    }
}
