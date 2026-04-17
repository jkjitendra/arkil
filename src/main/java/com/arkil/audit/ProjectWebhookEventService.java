package com.arkil.audit;

import com.arkil.project.Project;
import com.arkil.project.ProjectRepository;
import com.arkil.user.ArkilUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectWebhookEventService {

    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    public void userCreated(ArkilUser subjectUser,
                            ActorType actorType,
                            String actorId,
                            HttpServletRequest request,
                            String clientId,
                            UUID tenantId,
                            String source) {
        publishUserEvent(
                AuditEventType.USER_CREATED,
                "user.created",
                subjectUser,
                actorType,
                actorId,
                request,
                clientId,
                tenantId,
                "User created",
                Map.of("source", source)
        );
    }

    public void userUpdated(ArkilUser subjectUser,
                            ActorType actorType,
                            String actorId,
                            HttpServletRequest request,
                            String clientId,
                            UUID tenantId,
                            Collection<String> changedFields,
                            String source) {
        publishUserEvent(
                AuditEventType.USER_UPDATED,
                "user.updated",
                subjectUser,
                actorType,
                actorId,
                request,
                clientId,
                tenantId,
                "User updated",
                Map.of(
                        "source", source,
                        "changedFields", changedFields
                )
        );
    }

    public void userDeleted(ArkilUser subjectUser,
                            ActorType actorType,
                            String actorId,
                            HttpServletRequest request,
                            String clientId,
                            UUID tenantId,
                            String source,
                            boolean softDelete) {
        publishUserEvent(
                AuditEventType.USER_DELETED,
                "user.deleted",
                subjectUser,
                actorType,
                actorId,
                request,
                clientId,
                tenantId,
                "User deleted",
                Map.of(
                        "source", source,
                        "softDelete", softDelete
                )
        );
    }

    public void passwordChanged(ArkilUser subjectUser,
                                ActorType actorType,
                                String actorId,
                                HttpServletRequest request,
                                String clientId,
                                UUID tenantId,
                                boolean initialPasswordSet) {
        publishUserEvent(
                AuditEventType.USER_PASSWORD_CHANGED,
                "password.changed",
                subjectUser,
                actorType,
                actorId,
                request,
                clientId,
                tenantId,
                "Password changed",
                Map.of("initialPasswordSet", initialPasswordSet)
        );
    }

    public void userBlocked(ArkilUser subjectUser,
                            ActorType actorType,
                            String actorId,
                            HttpServletRequest request,
                            String clientId,
                            UUID tenantId,
                            String reason) {
        publishUserEvent(
                AuditEventType.USER_BLOCKED,
                "user.blocked",
                subjectUser,
                actorType,
                actorId,
                request,
                clientId,
                tenantId,
                "User blocked",
                Map.of("reason", reason != null ? reason : "")
        );
    }

    public void userUnblocked(ArkilUser subjectUser,
                              ActorType actorType,
                              String actorId,
                              HttpServletRequest request,
                              String clientId,
                              UUID tenantId) {
        publishUserEvent(
                AuditEventType.USER_UNBLOCKED,
                "user.unblocked",
                subjectUser,
                actorType,
                actorId,
                request,
                clientId,
                tenantId,
                "User unblocked",
                Map.of()
        );
    }

    public void sessionCreated(ArkilUser subjectUser,
                               ActorType actorType,
                               String actorId,
                               HttpServletRequest request,
                               String clientId,
                               UUID tenantId,
                               String authMethod) {
        publishUserEvent(
                AuditEventType.SESSION_CREATED,
                "session.created",
                subjectUser,
                actorType,
                actorId,
                request,
                clientId,
                tenantId,
                "Session created",
                Map.of("authMethod", authMethod)
        );
    }

    private void publishUserEvent(AuditEventType auditEventType,
                                  String webhookEventType,
                                  ArkilUser subjectUser,
                                  ActorType actorType,
                                  String actorId,
                                  HttpServletRequest request,
                                  String clientId,
                                  UUID tenantId,
                                  String details,
                                  Map<String, Object> data) {
        UUID resolvedTenantId = tenantId != null
                ? tenantId
                : subjectUser != null && subjectUser.getTenant() != null ? subjectUser.getTenant().getId() : null;

        List<Project> projects = resolveProjects(clientId, resolvedTenantId);
        if (projects.isEmpty()) {
            auditService.logSuccess(auditEventType,
                    actorId,
                    actorType,
                    subjectUser != null ? subjectUser.getId().toString() : null,
                    request);
            return;
        }

        Instant occurredAt = Instant.now();
        Map<UUID, Map<String, Object>> payloadsByProject = new LinkedHashMap<>();
        for (Project project : projects) {
            payloadsByProject.put(project.getId(), buildPayload(
                    webhookEventType,
                    occurredAt,
                    project,
                    actorType,
                    actorId,
                    subjectUser,
                    data
            ));
        }

        auditService.logEventWithWebhook(
                auditEventType,
                actorId,
                actorType,
                subjectUser != null ? subjectUser.getId().toString() : null,
                AuditOutcome.SUCCESS,
                details,
                request,
                payloadsByProject
        );
    }

    private List<Project> resolveProjects(String clientId, UUID tenantId) {
        if (clientId != null && clientId.startsWith("proj_")) {
            String slug = clientId.substring("proj_".length());
            return projectRepository.findBySlug(slug)
                    .filter(project -> project.getDeletedAt() == null)
                    .map(List::of)
                    .orElseGet(List::of);
        }

        if (tenantId != null) {
            return projectRepository.findByTenantIdAndDeletedAtIsNull(tenantId);
        }

        return List.of();
    }

    private Map<String, Object> buildPayload(String webhookEventType,
                                             Instant occurredAt,
                                             Project project,
                                             ActorType actorType,
                                             String actorId,
                                             ArkilUser subjectUser,
                                             Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", webhookEventType);
        payload.put("occurredAt", occurredAt.toString());

        Map<String, Object> projectInfo = new LinkedHashMap<>();
        projectInfo.put("id", project.getId().toString());
        projectInfo.put("name", project.getName());
        projectInfo.put("slug", project.getSlug());
        projectInfo.put("clientId", "proj_" + project.getSlug());
        projectInfo.put("tenantId", project.getTenantId() != null ? project.getTenantId().toString() : null);
        payload.put("project", projectInfo);

        Map<String, Object> actorInfo = new LinkedHashMap<>();
        actorInfo.put("id", actorId);
        actorInfo.put("type", actorType.name().toLowerCase());
        payload.put("actor", actorInfo);

        if (subjectUser != null) {
            Map<String, Object> subjectInfo = new LinkedHashMap<>();
            subjectInfo.put("id", subjectUser.getId().toString());
            subjectInfo.put("username", subjectUser.getUsername());
            subjectInfo.put("email", subjectUser.getEmail());
            subjectInfo.put("displayName", subjectUser.getDisplayName());
            payload.put("subject", subjectInfo);
        }

        payload.put("data", data);
        return payload;
    }
}
