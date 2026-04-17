package com.arkil.audit;

import com.arkil.webhook.WebhookDispatchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Service for recording audit log entries.
 * Uses async processing to avoid blocking auth flows.
 * Optionally dispatches webhook events for project-scoped actions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final WebhookDispatchService webhookDispatchService;

    /**
     * Log an audit event asynchronously.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditEventType eventType,
                         String actorId,
                         ActorType actorType,
                         String targetId,
                         AuditOutcome outcome,
                         String details,
                         HttpServletRequest request) {

        AuditLog entry = AuditLog.builder()
                .eventType(eventType)
                .actorId(actorId)
                .actorType(actorType)
                .targetId(targetId)
                .outcome(outcome)
                .details(details)
                .ipAddress(extractIpAddress(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .build();

        auditLogRepository.save(entry);
        log.debug("Audit: {} by {} -> {}", eventType, actorId, outcome);
    }

    /**
     * Log an audit event AND dispatch it as a webhook to the project's subscribers.
     * Maps AuditEventType to webhook event names (e.g. USER_CREATED -> "user.created").
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEventWithWebhook(AuditEventType eventType,
                                    String actorId,
                                    ActorType actorType,
                                    String targetId,
                                    AuditOutcome outcome,
                                    String details,
                                    HttpServletRequest request,
                                    UUID projectId,
                                    Map<String, Object> webhookPayload) {
        logEventWithWebhook(eventType, actorId, actorType, targetId, outcome, details, request,
                projectId != null ? Map.of(projectId, webhookPayload) : Map.of());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEventWithWebhook(AuditEventType eventType,
                                    String actorId,
                                    ActorType actorType,
                                    String targetId,
                                    AuditOutcome outcome,
                                    String details,
                                    HttpServletRequest request,
                                    Map<UUID, Map<String, Object>> webhookPayloadsByProject) {
        logEvent(eventType, actorId, actorType, targetId, outcome, details, request);

        if (outcome != AuditOutcome.SUCCESS || webhookPayloadsByProject == null || webhookPayloadsByProject.isEmpty()) {
            return;
        }

        String webhookEvent = mapToWebhookEvent(eventType);
        if (webhookEvent == null) {
            return;
        }

        webhookPayloadsByProject.forEach((projectId, payload) -> {
            if (projectId != null && payload != null) {
                webhookDispatchService.dispatchEvent(projectId, webhookEvent, payload);
            }
        });
    }

    /**
     * Convenience method for successful events.
     */
    public void logSuccess(AuditEventType eventType,
                           String actorId,
                           ActorType actorType,
                           String targetId,
                           HttpServletRequest request) {
        logEvent(eventType, actorId, actorType, targetId, AuditOutcome.SUCCESS, null, request);
    }

    /**
     * Convenience method for failed events.
     */
    public void logFailure(AuditEventType eventType,
                           String actorId,
                           ActorType actorType,
                           String targetId,
                           String reason,
                           HttpServletRequest request) {
        logEvent(eventType, actorId, actorType, targetId, AuditOutcome.FAILURE, reason, request);
    }

    /**
     * Map audit event types to webhook event names.
     */
    private String mapToWebhookEvent(AuditEventType eventType) {
        return switch (eventType) {
            case USER_CREATED -> "user.created";
            case USER_UPDATED -> "user.updated";
            case USER_DELETED -> "user.deleted";
            case USER_BLOCKED -> "user.blocked";
            case USER_UNBLOCKED -> "user.unblocked";
            case USER_PASSWORD_CHANGED -> "password.changed";
            case SESSION_CREATED -> "session.created";
            default -> null;
        };
    }

    private String extractIpAddress(HttpServletRequest request) {
        if (request == null) return null;

        // Check for proxy headers
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
