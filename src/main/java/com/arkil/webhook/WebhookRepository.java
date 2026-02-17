package com.arkil.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    List<Webhook> findByProjectId(UUID projectId);

    List<Webhook> findByProjectIdAndEnabledTrue(UUID projectId);

    Optional<Webhook> findByProjectIdAndId(UUID projectId, UUID id);

    long countByProjectId(UUID projectId);
}
