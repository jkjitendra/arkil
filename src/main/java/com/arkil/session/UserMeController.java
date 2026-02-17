package com.arkil.session;

import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.credential.social.SocialIdentity;
import com.arkil.credential.social.SocialIdentityRepository;
import com.arkil.tenant.Tenant;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User self-service controller.
 * Part of the Session/UX API surface (Surface B).
 *
 * All endpoints require a valid JWT with the user's UUID as the sub claim.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "User", description = "User self-service operations")
@SecurityRequirement(name = "bearerAuth")
public class UserMeController {

    private final UserRepository userRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final SocialIdentityRepository socialIdentityRepository;

    // ─────────────────────────────────────────────────────────────────
    // Profile
    // ─────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get current user profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        ArkilUser user = resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        boolean hasPassword = passwordCredentialRepository.existsByUser_Id(user.getId());
        List<SocialIdentity> socialIdentities = socialIdentityRepository.findByUserId(user.getId());

        Tenant tenant = user.getTenant();

        return ResponseEntity.ok(new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                Boolean.TRUE.equals(user.getEnabled()),
                hasPassword,
                socialIdentities.stream()
                        .map(si -> new ConnectedAccount(
                                si.getProvider(),
                                si.getProviderEmail(),
                                si.getProviderDisplayName(),
                                si.getPictureUrl(),
                                si.getCreatedAt() != null ? si.getCreatedAt().toString() : null
                        ))
                        .toList(),
                new TenantInfo(
                        tenant.getId(),
                        tenant.getName(),
                        tenant.getSlug()
                ),
                user.getRoles().stream()
                        .map(r -> r.getName())
                        .collect(Collectors.toSet()),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
                user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null
        ));
    }

    @PutMapping
    @Operation(summary = "Update current user profile")
    public ResponseEntity<?> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {

        ArkilUser user = resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }

        userRepository.save(user);
        log.info("User {} updated profile", user.getEmail());

        return ResponseEntity.ok(Map.of(
                "message", "Profile updated",
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername()
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Password Change
    // ─────────────────────────────────────────────────────────────────

    @PutMapping("/password")
    @Operation(summary = "Change password (requires current password)")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {

        ArkilUser user = resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Optional<PasswordCredential> credentialOpt = passwordCredentialRepository.findByUser_Id(user.getId());

        if (credentialOpt.isEmpty()) {
            // User signed up via social login only — set initial password
            PasswordCredential newCredential = PasswordCredential.builder()
                    .user(user)
                    .passwordHash(passwordEncoder.encode(request.getNewPassword()))
                    .build();
            passwordCredentialRepository.save(newCredential);
            log.info("User {} set initial password", user.getEmail());
            return ResponseEntity.ok(Map.of("message", "Password set successfully"));
        }

        PasswordCredential credential = credentialOpt.get();

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), credential.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_password",
                    "message", "Current password is incorrect"
            ));
        }

        // Prevent setting same password
        if (passwordEncoder.matches(request.getNewPassword(), credential.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "same_password",
                    "message", "New password must be different from current password"
            ));
        }

        credential.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        passwordCredentialRepository.save(credential);
        log.info("User {} changed password", user.getEmail());

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    // ─────────────────────────────────────────────────────────────────
    // Account Deletion
    // ─────────────────────────────────────────────────────────────────

    @DeleteMapping
    @Operation(summary = "Delete current user account")
    public ResponseEntity<Map<String, String>> deleteAccount(Authentication authentication) {
        ArkilUser user = resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        // Disable the account (soft delete — don't hard-delete to preserve audit trail)
        user.setEnabled(false);
        userRepository.save(user);

        log.warn("User {} requested account deletion — account disabled", user.getEmail());

        return ResponseEntity.ok(Map.of(
                "message", "Account has been disabled. Contact support if you want to permanently delete your data."
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private ArkilUser resolveUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        UUID userId = extractUserId(auth);
        if (userId == null) {
            return null;
        }

        return userRepository.findById(userId).orElse(null);
    }

    private UUID extractUserId(Authentication auth) {
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null) {
                try {
                    return UUID.fromString(sub);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ignored) {}

        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────────────

    public record ProfileResponse(
            UUID id,
            String username,
            String email,
            String displayName,
            boolean emailVerified,
            boolean enabled,
            boolean hasPassword,
            List<ConnectedAccount> connectedAccounts,
            TenantInfo tenant,
            java.util.Set<String> roles,
            String createdAt,
            String lastLoginAt
    ) {}

    public record ConnectedAccount(
            String provider,
            String email,
            String displayName,
            String pictureUrl,
            String connectedAt
    ) {}

    public record TenantInfo(
            UUID id,
            String name,
            String slug
    ) {}

    @Data
    public static class UpdateProfileRequest {
        @Size(max = 100, message = "Display name must be 100 characters or less")
        private String displayName;
    }

    @Data
    public static class ChangePasswordRequest {
        private String currentPassword; // Optional for social-only users setting initial password

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String newPassword;
    }
}
