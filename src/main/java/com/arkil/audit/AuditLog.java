package com.arkil.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log entry for security-relevant events.
 * Covers: config changes, auth attempts, admin actions.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_event_type", columnList = "eventType"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        @Index(name = "idx_audit_actor", columnList = "actorId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditEventType eventType;

    @Column(nullable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Who performed the action (user ID, client ID, or "system").
     */
    private String actorId;

    /**
     * Type of actor: USER, CLIENT, ADMIN, SYSTEM
     */
    @Enumerated(EnumType.STRING)
    private ActorType actorType;

    /**
     * Target resource (e.g., userId, clientId, policyId).
     */
    private String targetId;

    /**
     * IP address of the request origin.
     */
    private String ipAddress;

    /**
     * User agent string.
     */
    private String userAgent;

    /**
     * Outcome of the event.
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AuditOutcome outcome = AuditOutcome.SUCCESS;

    /**
     * Additional details as JSON (e.g., changed fields, error message).
     */
    @Column(columnDefinition = "TEXT")
    private String details;
}
