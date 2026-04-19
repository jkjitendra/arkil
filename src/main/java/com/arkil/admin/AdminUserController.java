package com.arkil.admin;

import com.arkil.audit.ActorType;
import com.arkil.audit.ProjectWebhookEventService;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin API controller for user management.
 * Part of Surface C (Admin/Dashboard APIs).
 *
 * Requires: admin OAuth token with arkil:admin scope.
 * TENANT_ADMINs can only see and manage users within their own tenant.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "User administration (admin only)")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserRepository userRepository;
    private final ProjectWebhookEventService projectWebhookEventService;

    @GetMapping
    @Operation(summary = "List users (scoped to tenant)")
    public ResponseEntity<Page<UserDto>> listUsers(Pageable pageable, Authentication auth) {
        UUID tenantId = getTenantId(auth);

        Page<ArkilUser> users;
        if (tenantId != null) {
            users = userRepository.findByTenantId(tenantId, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return ResponseEntity.ok(users.map(this::toDto));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<UserDto> getUser(@PathVariable UUID userId, Authentication auth) {
        Optional<ArkilUser> userOpt = findUserInTenant(userId, auth);
        return userOpt
                .map(u -> ResponseEntity.ok(toDto(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user")
    public ResponseEntity<?> updateUser(
            @PathVariable UUID userId,
            @RequestBody UpdateUserRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {

        ArkilUser user = findUserInTenant(userId, auth).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean previousEnabled = user.getEnabled();
        Boolean previousEmailVerified = user.getEmailVerified();
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.emailVerified() != null) {
            user.setEmailVerified(request.emailVerified());
        }

        userRepository.save(user);
        String adminActorId = extractActorId(auth);

        if (request.enabled() != null && !request.enabled().equals(previousEnabled)) {
            if (Boolean.TRUE.equals(request.enabled())) {
                projectWebhookEventService.userUnblocked(
                        user, ActorType.ADMIN, adminActorId, httpRequest, null, user.getTenant().getId());
            } else {
                projectWebhookEventService.userBlocked(
                        user, ActorType.ADMIN, adminActorId, httpRequest, null, user.getTenant().getId(), "admin_update");
            }
        }

        if (request.emailVerified() != null && !request.emailVerified().equals(previousEmailVerified)) {
            projectWebhookEventService.userUpdated(
                    user,
                    ActorType.ADMIN,
                    adminActorId,
                    httpRequest,
                    null,
                    user.getTenant().getId(),
                    java.util.List.of("emailVerified"),
                    "admin_console"
            );
        }

        log.info("Admin updated user {}", userId);

        return ResponseEntity.ok(toDto(user));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable UUID userId,
                                                          Authentication auth,
                                                          HttpServletRequest httpRequest) {
        ArkilUser user = findUserInTenant(userId, auth).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        user.setEnabled(false);
        userRepository.save(user);
        projectWebhookEventService.userDeleted(
                user,
                ActorType.ADMIN,
                extractActorId(auth),
                httpRequest,
                null,
                user.getTenant().getId(),
                "admin_console",
                true
        );
        log.warn("Admin deleted user {}", userId);

        return ResponseEntity.ok(Map.of("message", "User deleted"));
    }

    @PostMapping("/{userId}/block")
    @Operation(summary = "Block user (disable account)")
    public ResponseEntity<?> blockUser(
            @PathVariable UUID userId,
            @RequestBody(required = false) BlockRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {

        ArkilUser user = findUserInTenant(userId, auth).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        user.setEnabled(false);
        userRepository.save(user);
        projectWebhookEventService.userBlocked(
                user,
                ActorType.ADMIN,
                extractActorId(auth),
                httpRequest,
                null,
                user.getTenant().getId(),
                request != null ? request.reason() : null
        );

        log.warn("Admin blocked user {}: {}", userId, request != null ? request.reason() : "No reason");

        return ResponseEntity.ok(Map.of("message", "User blocked"));
    }

    @PostMapping("/{userId}/unblock")
    @Operation(summary = "Unblock user (enable account)")
    public ResponseEntity<Map<String, String>> unblockUser(@PathVariable UUID userId,
                                                           Authentication auth,
                                                           HttpServletRequest httpRequest) {
        ArkilUser user = findUserInTenant(userId, auth).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        user.setEnabled(true);
        userRepository.save(user);
        projectWebhookEventService.userUnblocked(
                user,
                ActorType.ADMIN,
                extractActorId(auth),
                httpRequest,
                null,
                user.getTenant().getId()
        );

        log.info("Admin unblocked user {}", userId);

        return ResponseEntity.ok(Map.of("message", "User unblocked"));
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Find a user, scoped to the caller's tenant if tenant_id is present in JWT.
     * Returns empty if the user doesn't exist or doesn't belong to the tenant.
     */
    private Optional<ArkilUser> findUserInTenant(UUID userId, Authentication auth) {
        UUID tenantId = getTenantId(auth);
        if (tenantId != null) {
            return userRepository.findByIdAndTenantId(userId, tenantId);
        }
        return userRepository.findById(userId);
    }

    private UUID getTenantId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String tenantIdStr = jwt.getClaimAsString("tenant_id");
            if (tenantIdStr != null) {
                try {
                    return UUID.fromString(tenantIdStr);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    private String extractActorId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return auth != null ? auth.getName() : "admin";
    }

    // ─────────────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────────────

    private UserDto toDto(ArkilUser user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getEmailVerified()),
                user.getTenant().getId(),
                user.getTenant().getName(),
                user.getTenant().getSlug(),
                user.getRoles().stream().map(role -> role.getName()).sorted().toList(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
                user.getUpdatedAt() != null ? user.getUpdatedAt().toString() : null,
                user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null
        );
    }

    public record UserDto(
            UUID id,
            String username,
            String email,
            String displayName,
            boolean enabled,
            boolean emailVerified,
            UUID tenantId,
            String tenantName,
            String tenantSlug,
            List<String> roles,
            String createdAt,
            String updatedAt,
            String lastLoginAt
    ) {}

    public record UpdateUserRequest(
            Boolean enabled,
            Boolean emailVerified
    ) {}

    public record BlockRequest(
            String reason
    ) {}
}
