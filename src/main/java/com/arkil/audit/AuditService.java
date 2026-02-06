package com.arkil.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for recording audit log entries.
 * Uses async processing to avoid blocking auth flows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

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
