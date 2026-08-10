package com.arkil.audit;

import com.arkil.webhook.WebhookDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Performs database persistence and webhook dispatch away from the request thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class AsyncAuditEventService {

    private final AuditLogRepository auditLogRepository;
    private final WebhookDispatchService webhookDispatchService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditEventType eventType,
                         String actorId,
                         ActorType actorType,
                         String targetId,
                         AuditOutcome outcome,
                         String details,
                         AuditRequestDetails requestDetails) {
        persistAuditEvent(eventType, actorId, actorType, targetId, outcome, details, requestDetails);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEventWithWebhook(AuditEventType eventType,
                                    String actorId,
                                    ActorType actorType,
                                    String targetId,
                                    AuditOutcome outcome,
                                    String details,
                                    AuditRequestDetails requestDetails,
                                    Map<UUID, Map<String, Object>> webhookPayloadsByProject) {
        persistAuditEvent(eventType, actorId, actorType, targetId, outcome, details, requestDetails);

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

    private void persistAuditEvent(AuditEventType eventType,
                                   String actorId,
                                   ActorType actorType,
                                   String targetId,
                                   AuditOutcome outcome,
                                   String details,
                                   AuditRequestDetails requestDetails) {
        AuditLog entry = AuditLog.builder()
                .eventType(eventType)
                .actorId(actorId)
                .actorType(actorType)
                .targetId(targetId)
                .outcome(outcome)
                .details(details)
                .ipAddress(requestDetails.ipAddress())
                .userAgent(requestDetails.userAgent())
                .build();

        auditLogRepository.save(entry);
        log.debug("Audit: {} by {} -> {}", eventType, actorId, outcome);
    }

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
}
