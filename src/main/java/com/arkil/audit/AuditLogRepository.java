package com.arkil.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByActorIdOrderByTimestampDesc(String actorId);

    List<AuditLog> findByEventTypeAndTimestampAfter(AuditEventType eventType, Instant since);

    List<AuditLog> findByTargetIdOrderByTimestampDesc(String targetId);

    long countByEventTypeAndActorIdAndTimestampAfter(
            AuditEventType eventType,
            String actorId,
            Instant since
    );
}
