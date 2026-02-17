package com.arkil.webhook;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Webhook configuration for a project.
 * Developers configure URLs to receive notifications about auth events.
 */
@Entity
@Table(name = "webhooks", indexes = {
        @Index(name = "idx_webhook_project", columnList = "projectId"),
        @Index(name = "idx_webhook_enabled", columnList = "projectId, enabled")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    /**
     * The URL to POST event payloads to.
     */
    @Column(nullable = false, length = 2048)
    private String url;

    /**
     * HMAC-SHA256 signing secret (auto-generated, encrypted at rest).
     */
    @Column(nullable = false, length = 512)
    private String secret;

    /**
     * Event types this webhook subscribes to.
     * Stored as comma-separated: "user.created,session.created,user.blocked"
     */
    @Column(nullable = false, length = 1024)
    private String events;

    /**
     * Optional human-readable description.
     */
    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    // ─── Helpers ──────────────────────────────────────────────────

    /**
     * Get the set of subscribed event types.
     */
    public Set<String> getEventSet() {
        if (events == null || events.isBlank()) return Set.of();
        Set<String> set = new HashSet<>();
        for (String e : events.split(",")) {
            String trimmed = e.trim();
            if (!trimmed.isEmpty()) set.add(trimmed);
        }
        return set;
    }

    /**
     * Set subscribed events from a collection.
     */
    public void setEventSet(Set<String> eventSet) {
        this.events = String.join(",", eventSet);
    }

    /**
     * Check if this webhook subscribes to a given event type.
     */
    public boolean subscribesTo(String eventType) {
        return getEventSet().contains(eventType) || getEventSet().contains("*");
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
