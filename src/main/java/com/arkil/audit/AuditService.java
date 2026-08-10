package com.arkil.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Captures request-scoped audit data before handing it to the asynchronous audit worker.
 * Servlet request objects must not cross the request-thread boundary because Tomcat may
 * recycle them once the HTTP response has been committed.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AsyncAuditEventService asyncAuditEventService;

    public void logEvent(AuditEventType eventType,
                         String actorId,
                         ActorType actorType,
                         String targetId,
                         AuditOutcome outcome,
                         String details,
                         HttpServletRequest request) {
        asyncAuditEventService.logEvent(
                eventType, actorId, actorType, targetId, outcome, details, captureRequestDetails(request));
    }

    /**
     * Log an audit event and dispatch it to the project's webhook subscribers.
     */
    public void logEventWithWebhook(AuditEventType eventType,
                                    String actorId,
                                    ActorType actorType,
                                    String targetId,
                                    AuditOutcome outcome,
                                    String details,
                                    HttpServletRequest request,
                                    UUID projectId,
                                    Map<String, Object> webhookPayload) {
        asyncAuditEventService.logEventWithWebhook(
                eventType,
                actorId,
                actorType,
                targetId,
                outcome,
                details,
                captureRequestDetails(request),
                projectId != null ? Map.of(projectId, webhookPayload) : Map.of());
    }

    public void logEventWithWebhook(AuditEventType eventType,
                                    String actorId,
                                    ActorType actorType,
                                    String targetId,
                                    AuditOutcome outcome,
                                    String details,
                                    HttpServletRequest request,
                                    Map<UUID, Map<String, Object>> webhookPayloadsByProject) {
        asyncAuditEventService.logEventWithWebhook(
                eventType,
                actorId,
                actorType,
                targetId,
                outcome,
                details,
                captureRequestDetails(request),
                webhookPayloadsByProject);
    }

    public void logSuccess(AuditEventType eventType,
                           String actorId,
                           ActorType actorType,
                           String targetId,
                           HttpServletRequest request) {
        logEvent(eventType, actorId, actorType, targetId, AuditOutcome.SUCCESS, null, request);
    }

    public void logFailure(AuditEventType eventType,
                           String actorId,
                           ActorType actorType,
                           String targetId,
                           String reason,
                           HttpServletRequest request) {
        logEvent(eventType, actorId, actorType, targetId, AuditOutcome.FAILURE, reason, request);
    }

    private AuditRequestDetails captureRequestDetails(HttpServletRequest request) {
        if (request == null) {
            return AuditRequestDetails.empty();
        }

        return new AuditRequestDetails(
                extractIpAddress(request),
                request.getHeader("User-Agent"));
    }

    private String extractIpAddress(HttpServletRequest request) {
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
