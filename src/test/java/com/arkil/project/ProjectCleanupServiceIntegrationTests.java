package com.arkil.project;

import com.arkil.client.ClientAuthPolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProjectCleanupServiceIntegrationTests {

    @Autowired private ProjectService projectService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private ClientAuthPolicyRepository clientAuthPolicyRepository;
    @Autowired private ProjectCleanupService projectCleanupService;

    @Test
    void purgeExpiredDeletedProjectsRemovesProjectAndRegisteredClientArtifacts() {
        ProjectService.ProjectWithKeys created = projectService.createProject(
                new ProjectService.CreateProjectRequest(
                        "Cleanup Project",
                        "cleanup-project-" + UUID.randomUUID().toString().substring(0, 8),
                        "cleanup",
                        Project.Environment.DEVELOPMENT,
                        List.of("http://localhost:3000"),
                        List.of("http://localhost:3000/callback")
                ),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000001")
        );

        Project project = projectRepository.findById(created.project().getId()).orElseThrow();
        String clientId = "proj_" + project.getSlug();
        String registeredClientId = project.getRegisteredClientId();

        project.setActive(false);
        project.setDeletedAt(Instant.now().minus(8, ChronoUnit.DAYS));
        projectRepository.saveAndFlush(project);

        projectCleanupService.purgeExpiredDeletedProjects();

        assertTrue(projectRepository.findById(project.getId()).isEmpty());
        assertTrue(apiKeyRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).isEmpty());
        assertFalse(clientAuthPolicyRepository.existsByClientId(clientId));
        assertTrue(clientAuthPolicyRepository.findByRegisteredClientInternalId(registeredClientId).isEmpty());
    }
}
