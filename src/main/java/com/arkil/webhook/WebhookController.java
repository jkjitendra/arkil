package com.arkil.webhook;

import com.arkil.project.Project;
import com.arkil.project.ProjectRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * REST API for managing project webhooks.
 * All endpoints require project ownership via JWT tenant_id claim.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Configure webhook endpoints for auth events")
@SecurityRequirement(name = "bearerAuth")
public class WebhookController {

    private final WebhookRepository webhookRepository;
    private final ProjectRepository projectRepository;
    private final WebhookDispatchService webhookDispatchService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MAX_WEBHOOKS_PER_PROJECT = 10;

    /**
     * Supported webhook event types.
     */
    public static final Set<String> SUPPORTED_EVENTS = Set.of(
            "user.created",
            "user.updated",
            "user.deleted",
            "user.blocked",
            "user.unblocked",
            "session.created",
            "password.changed",
            "*"  // wildcard: all events
    );

    // ─── List Webhooks ──────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List webhooks for a project")
    public ResponseEntity<?> listWebhooks(@PathVariable UUID projectId, Authentication auth) {
        verifyProjectAccess(projectId, auth);

        List<WebhookDto> webhooks = webhookRepository.findByProjectId(projectId).stream()
                .map(this::toDto)
                .toList();

        return ResponseEntity.ok(webhooks);
    }

    // ─── Create Webhook ─────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create a webhook")
    public ResponseEntity<?> createWebhook(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateWebhookRequest request,
            Authentication auth) {

        verifyProjectAccess(projectId, auth);

        // Check limit
        long count = webhookRepository.countByProjectId(projectId);
        if (count >= MAX_WEBHOOKS_PER_PROJECT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "limit_exceeded",
                    "message", "Maximum " + MAX_WEBHOOKS_PER_PROJECT + " webhooks per project",
                    "timestamp", Instant.now().toString()
            ));
        }

        // Validate event types
        for (String event : request.events) {
            if (!SUPPORTED_EVENTS.contains(event)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "invalid_event",
                        "message", "Unsupported event type: " + event + ". Supported: " + SUPPORTED_EVENTS,
                        "timestamp", Instant.now().toString()
                ));
            }
        }

        // Generate signing secret
        String signingSecret = generateSigningSecret();

        Webhook webhook = Webhook.builder()
                .projectId(projectId)
                .url(request.url)
                .secret(signingSecret)
                .events(String.join(",", request.events))
                .description(request.description)
                .enabled(true)
                .build();

        webhookRepository.save(webhook);
        log.info("Webhook created: id={}, project={}, url={}", webhook.getId(), projectId, request.url);

        // Return DTO with the plaintext secret (only shown once)
        WebhookCreatedDto dto = new WebhookCreatedDto(
                webhook.getId().toString(),
                webhook.getUrl(),
                signingSecret,  // Only returned on creation
                webhook.getEvents(),
                webhook.getDescription(),
                webhook.isEnabled(),
                webhook.getCreatedAt().toString()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // ─── Update Webhook ─────────────────────────────────────────

    @PutMapping("/{webhookId}")
    @Operation(summary = "Update a webhook")
    public ResponseEntity<?> updateWebhook(
            @PathVariable UUID projectId,
            @PathVariable UUID webhookId,
            @Valid @RequestBody UpdateWebhookRequest request,
            Authentication auth) {

        verifyProjectAccess(projectId, auth);

        Webhook webhook = webhookRepository.findByProjectIdAndId(projectId, webhookId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook not found"));

        if (request.url != null) webhook.setUrl(request.url);
        if (request.events != null) {
            for (String event : request.events) {
                if (!SUPPORTED_EVENTS.contains(event)) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "invalid_event",
                            "message", "Unsupported event type: " + event,
                            "timestamp", Instant.now().toString()
                    ));
                }
            }
            webhook.setEvents(String.join(",", request.events));
        }
        if (request.description != null) webhook.setDescription(request.description);
        if (request.enabled != null) webhook.setEnabled(request.enabled);

        webhookRepository.save(webhook);
        log.info("Webhook updated: id={}, project={}", webhookId, projectId);

        return ResponseEntity.ok(toDto(webhook));
    }

    // ─── Delete Webhook ─────────────────────────────────────────

    @DeleteMapping("/{webhookId}")
    @Operation(summary = "Delete a webhook")
    public ResponseEntity<?> deleteWebhook(
            @PathVariable UUID projectId,
            @PathVariable UUID webhookId,
            Authentication auth) {

        verifyProjectAccess(projectId, auth);

        Webhook webhook = webhookRepository.findByProjectIdAndId(projectId, webhookId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook not found"));

        webhookRepository.delete(webhook);
        log.info("Webhook deleted: id={}, project={}", webhookId, projectId);

        return ResponseEntity.noContent().build();
    }

    // ─── Test Webhook (Ping) ────────────────────────────────────

    @PostMapping("/{webhookId}/test")
    @Operation(summary = "Send a test ping to a webhook")
    public ResponseEntity<?> testWebhook(
            @PathVariable UUID projectId,
            @PathVariable UUID webhookId,
            Authentication auth) {

        verifyProjectAccess(projectId, auth);

        Webhook webhook = webhookRepository.findByProjectIdAndId(projectId, webhookId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook not found"));

        boolean success = webhookDispatchService.sendPing(webhook);

        return ResponseEntity.ok(Map.of(
                "success", success,
                "message", success ? "Ping delivered successfully" : "Ping delivery failed — check the URL and try again",
                "timestamp", Instant.now().toString()
        ));
    }

    // ─── Supported Events ───────────────────────────────────────

    @GetMapping("/events")
    @Operation(summary = "List supported webhook event types")
    public ResponseEntity<?> listSupportedEvents() {
        return ResponseEntity.ok(SUPPORTED_EVENTS.stream().sorted().toList());
    }

    // ─── Helpers ────────────────────────────────────────────────

    private void verifyProjectAccess(UUID projectId, Authentication auth) {
        UUID tenantId = getTenantId(auth);
        UUID ownerId = getOwnerId(auth);

        Project project;
        if (tenantId != null) {
            project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        } else {
            project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));
            if (!project.getOwnerId().equals(ownerId)) {
                throw new SecurityException("Access denied to project");
            }
        }

        if (project.getDeletedAt() != null) {
            throw new IllegalArgumentException("Project not found");
        }
    }

    private UUID getOwnerId(Authentication auth) {
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new SecurityException("Unable to determine user identity");
    }

    private UUID getTenantId(Authentication auth) {
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String tenantIdStr = jwt.getClaimAsString("tenant_id");
            if (tenantIdStr != null) {
                return UUID.fromString(tenantIdStr);
            }
        }
        return null;
    }

    private String generateSigningSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }

    private WebhookDto toDto(Webhook webhook) {
        return new WebhookDto(
                webhook.getId().toString(),
                webhook.getUrl(),
                webhook.getEvents(),
                webhook.getDescription(),
                webhook.isEnabled(),
                webhook.getCreatedAt().toString(),
                webhook.getUpdatedAt() != null ? webhook.getUpdatedAt().toString() : null
        );
    }

    // ─── DTOs ───────────────────────────────────────────────────

    record CreateWebhookRequest(
            @NotBlank(message = "URL is required") String url,
            @NotEmpty(message = "At least one event type is required") Set<String> events,
            String description
    ) {}

    record UpdateWebhookRequest(
            String url,
            Set<String> events,
            String description,
            Boolean enabled
    ) {}

    record WebhookDto(
            String id,
            String url,
            String events,
            String description,
            boolean enabled,
            String createdAt,
            String updatedAt
    ) {}

    record WebhookCreatedDto(
            String id,
            String url,
            String signingSecret,
            String events,
            String description,
            boolean enabled,
            String createdAt
    ) {}
}
