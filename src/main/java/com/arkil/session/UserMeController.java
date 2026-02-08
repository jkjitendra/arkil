package com.arkil.session;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User self-service controller.
 * Part of the Session/UX API surface (Surface B).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "User", description = "User self-service operations")
@SecurityRequirement(name = "bearerAuth")
public class UserMeController {

    // ─────────────────────────────────────────────────────────────────
    // Profile
    // ─────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get current user profile")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        // TODO: Return full user profile from database
        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities().stream()
                        .map(Object::toString)
                        .toList()
        ));
    }

    @PutMapping
    @Operation(summary = "Update current user profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        // TODO: Update user profile in database
        log.info("User {} updating profile: displayName={}", authentication.getName(), request.getDisplayName());

        return ResponseEntity.ok(Map.of(
                "message", "Profile updated",
                "displayName", request.getDisplayName()
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Password Change
    // ─────────────────────────────────────────────────────────────────

    @PutMapping("/password")
    @Operation(summary = "Change password (requires current password)")
    public ResponseEntity<Map<String, String>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        // TODO: Verify current password and update to new password
        log.info("User {} changing password", authentication.getName());

        return ResponseEntity.ok(Map.of(
                "message", "Password changed successfully"
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Account Deletion
    // ─────────────────────────────────────────────────────────────────

    @DeleteMapping
    @Operation(summary = "Delete current user account")
    public ResponseEntity<Map<String, String>> deleteAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        // TODO: Soft-delete or hard-delete user account
        log.warn("User {} requested account deletion", authentication.getName());

        return ResponseEntity.ok(Map.of(
                "message", "Account deletion scheduled"
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // Request DTOs
    // ─────────────────────────────────────────────────────────────────

    @Data
    public static class UpdateProfileRequest {
        private String displayName;
        private String avatarUrl;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "Current password is required")
        private String currentPassword;

        @NotBlank(message = "New password is required")
        private String newPassword;
    }
}
