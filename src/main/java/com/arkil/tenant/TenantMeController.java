package com.arkil.tenant;

import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Returns tenant information for the currently authenticated user.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenant/me")
@RequiredArgsConstructor
@Tag(name = "Tenant", description = "Tenant self-service operations")
@SecurityRequirement(name = "bearerAuth")
public class TenantMeController {

    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Get current user's tenant info")
    public ResponseEntity<?> getTenantInfo(Authentication auth) {
        UUID userId = extractUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        ArkilUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        Tenant tenant = user.getTenant();
        return ResponseEntity.ok(new TenantInfoResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                Boolean.TRUE.equals(tenant.getEnabled()),
                tenant.getCreatedAt() != null ? tenant.getCreatedAt().toString() : null
        ));
    }

    private UUID extractUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;

        if (auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null) {
                try { return UUID.fromString(sub); } catch (IllegalArgumentException ignored) {}
            }
        }

        try { return UUID.fromString(auth.getName()); } catch (IllegalArgumentException ignored) {}
        return null;
    }

    public record TenantInfoResponse(
            UUID id,
            String name,
            String slug,
            boolean enabled,
            String createdAt
    ) {}
}
