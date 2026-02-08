package com.arkil.admin;

import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin API controller for user management.
 * Part of Surface C (Admin/Dashboard APIs).
 * 
 * Requires: admin OAuth token with arkil:admin scope
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "User administration (admin only)")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List all users (paginated)")
    public ResponseEntity<Page<UserDto>> listUsers(Pageable pageable) {
        Page<ArkilUser> users = userRepository.findAll(pageable);
        return ResponseEntity.ok(users.map(this::toDto));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<UserDto> getUser(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .map(u -> ResponseEntity.ok(toDto(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable UUID userId,
            @RequestBody UpdateUserRequest request) {

        ArkilUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.emailVerified() != null) {
            user.setEmailVerified(request.emailVerified());
        }

        userRepository.save(user);

        log.info("Admin updated user {}", userId);

        return ResponseEntity.ok(toDto(user));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable UUID userId) {
        if (!userRepository.existsById(userId)) {
            return ResponseEntity.notFound().build();
        }

        userRepository.deleteById(userId);
        log.warn("Admin deleted user {}", userId);

        return ResponseEntity.ok(Map.of("message", "User deleted"));
    }

    @PostMapping("/{userId}/block")
    @Operation(summary = "Block user (disable account)")
    public ResponseEntity<Map<String, String>> blockUser(
            @PathVariable UUID userId,
            @RequestBody(required = false) BlockRequest request) {

        ArkilUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setEnabled(false);
        // TODO: Add blocked reason and blockedAt fields

        userRepository.save(user);

        log.warn("Admin blocked user {}: {}", userId, request != null ? request.reason() : "No reason");

        return ResponseEntity.ok(Map.of("message", "User blocked"));
    }

    @PostMapping("/{userId}/unblock")
    @Operation(summary = "Unblock user (enable account)")
    public ResponseEntity<Map<String, String>> unblockUser(@PathVariable UUID userId) {
        ArkilUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setEnabled(true);
        userRepository.save(user);

        log.info("Admin unblocked user {}", userId);

        return ResponseEntity.ok(Map.of("message", "User unblocked"));
    }

    // ─────────────────────────────────────────────────────────────────
    // DTOs
    // ─────────────────────────────────────────────────────────────────

    private UserDto toDto(ArkilUser user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getEmailVerified()),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );
    }

    public record UserDto(
            UUID id,
            String username,
            String email,
            boolean enabled,
            boolean emailVerified,
            String createdAt
    ) {}

    public record UpdateUserRequest(
            Boolean enabled,
            Boolean emailVerified
    ) {}

    public record BlockRequest(
            String reason
    ) {}
}
