package com.arkil.project;

import com.arkil.webhook.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectCleanupService {

    private final ProjectRepository projectRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ProjectOAuthProviderRepository providerRepository;
    private final WebhookRepository webhookRepository;
    private final RegisteredClientBridgeService registeredClientBridgeService;

    @Value("${arkil.project.deletion-retention-days:7}")
    private long deletionRetentionDays;

    @Scheduled(cron = "${arkil.project.cleanup-cron:0 0 */6 * * *}")
    @Transactional
    public void purgeExpiredDeletedProjects() {
        Instant cutoff = Instant.now().minusSeconds(deletionRetentionDays * 24 * 60 * 60);
        List<Project> expiredProjects = projectRepository.findByDeletedAtBefore(cutoff);
        for (Project project : expiredProjects) {
            apiKeyRepository.deleteByProjectId(project.getId());
            providerRepository.deleteByProjectId(project.getId());
            webhookRepository.deleteByProjectId(project.getId());
            registeredClientBridgeService.deleteRegisteredClientForProject(project);
            projectRepository.delete(project);
            log.info("Permanently deleted expired project {}", project.getId());
        }
    }
}
