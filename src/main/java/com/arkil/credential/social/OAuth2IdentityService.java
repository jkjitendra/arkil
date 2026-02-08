package com.arkil.credential.social;

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

import java.time.Instant;
import java.util.Optional;

/**
 * Service for linking OAuth2 identities to ArkilUser accounts.
 * Handles first-time login (create user) and returning login (link existing).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2IdentityService {

    private final SocialIdentityRepository socialIdentityRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;

    /**
     * Process an OAuth2 login and return/create the associated ArkilUser.
     *
     * @param provider   The OAuth2 provider name (google, github, etc.)
     * @param oauth2User The authenticated OAuth2 user
     * @param tenantSlug The tenant slug (for now, use "demo" as default)
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

            log.info("Returning OAuth2 user: {}", identity.getUser().getUsername());
            return identity.getUser();
        }

        // New social login - find or create user
        Tenant tenant = tenantRepository.findBySlug(tenantSlug)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantSlug));

        // Try to find existing user by email in this tenant
        ArkilUser user = userRepository.findByTenantIdAndEmail(tenant.getId(), email)
                .orElseGet(() -> createNewUser(tenant, email, displayName));

        // Link the social identity
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

        return user;
    }

    private ArkilUser createNewUser(Tenant tenant, String email, String displayName) {
        // Generate username from email
        String username = email.split("@")[0] + "_" + System.currentTimeMillis() % 10000;

        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("USER")
                        .description("Standard user role")
                        .build()));

        ArkilUser user = ArkilUser.builder()
                .tenant(tenant)
                .username(username)
                .email(email)
                .displayName(displayName)
                .enabled(true)
                .emailVerified(true) // Trust the OAuth2 provider's email verification
                .build();

        user.getRoles().add(userRole);
        user = userRepository.save(user);

        log.info("Created new user via OAuth2: {}", user.getUsername());
        return user;
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
