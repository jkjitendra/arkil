package com.arkil.webhook;

import com.arkil.auth.EndUserRegistrationService;
import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.project.Project;
import com.arkil.project.ProjectRepository;
import com.arkil.tenant.Tenant;
import com.arkil.tenant.TenantRepository;
import com.arkil.user.ArkilUser;
import com.arkil.user.Role;
import com.arkil.user.RoleRepository;
import com.arkil.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookEventWiringIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordCredentialRepository passwordCredentialRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EndUserRegistrationService endUserRegistrationService;
    @Autowired private RecordingWebhookDispatchService recordingWebhookDispatchService;

    private Tenant tenant;
    private Project project;
    private ArkilUser user;

    @BeforeEach
    void setUp() {
        recordingWebhookDispatchService.clear();

        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("USER").description("User").build()));

        tenant = tenantRepository.findAll().stream()
                .filter(existing -> "webhook-events".equals(existing.getSlug()))
                .findFirst()
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .slug("webhook-events")
                        .name("Webhook Events")
                        .enabled(true)
                        .build()));

        project = projectRepository.findBySlug("webhook-events-app")
                .orElseGet(() -> projectRepository.save(Project.builder()
                        .name("Webhook Events App")
                        .slug("webhook-events-app")
                        .ownerId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                        .tenantId(tenant.getId())
                        .registeredClientId("webhook-events-internal")
                        .environment(Project.Environment.DEVELOPMENT)
                        .active(true)
                        .createdAt(Instant.now())
                        .build()));

        user = userRepository.findByTenantIdAndEmail(tenant.getId(), "events-user@example.com")
                .orElseGet(() -> {
                    ArkilUser created = userRepository.save(ArkilUser.builder()
                            .tenant(tenant)
                            .username("events-user")
                            .email("events-user@example.com")
                            .displayName("Events User")
                            .enabled(true)
                            .emailVerified(true)
                            .createdAt(Instant.now())
                            .build());
                    created.getRoles().add(userRole);
                    created = userRepository.save(created);
                    passwordCredentialRepository.save(PasswordCredential.builder()
                            .user(created)
                            .passwordHash(passwordEncoder.encode("Password123!"))
                            .algorithm("bcrypt")
                            .build());
                    return created;
                });

        user.setEnabled(true);
        user.setEmailVerified(true);
        user = userRepository.save(user);

        passwordCredentialRepository.findByUser_Id(user.getId())
                .ifPresentOrElse(existing -> {
                    existing.setPasswordHash(passwordEncoder.encode("Password123!"));
                    existing.setAlgorithm("bcrypt");
                    passwordCredentialRepository.save(existing);
                }, () -> passwordCredentialRepository.save(PasswordCredential.builder()
                        .user(user)
                        .passwordHash(passwordEncoder.encode("Password123!"))
                        .algorithm("bcrypt")
                        .build()));
    }

    @Test
    @DisplayName("End-user registration dispatches user.created to the project webhook pipeline")
    void registrationDispatchesUserCreated() {
        String email = "new-end-user@example.com";
        userRepository.findByTenantIdAndEmail(tenant.getId(), email).ifPresent(existing -> {
            passwordCredentialRepository.findByUser_Id(existing.getId()).ifPresent(passwordCredentialRepository::delete);
            userRepository.delete(existing);
        });

        endUserRegistrationService.registerEndUser(email, "Password123!", "proj_" + project.getSlug());

        RecordingWebhookDispatchService.Invocation invocation = recordingWebhookDispatchService.awaitSingleInvocation("user.created");
        assertThat(invocation.projectId()).isEqualTo(project.getId());
        assertThat(invocation.payload().get("subject")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("email", email);
        assertThat(invocation.payload()).containsEntry("event", "user.created");
    }

    @Test
    @DisplayName("Session API dispatches session.created with project context")
    void sessionApiDispatchesSessionCreated() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", user.getEmail(),
                                "password", "Password123!",
                                "clientId", "proj_" + project.getSlug()
                        ))))
                .andExpect(status().isOk());

        RecordingWebhookDispatchService.Invocation invocation = recordingWebhookDispatchService.awaitSingleInvocation("session.created");
        assertThat(invocation.projectId()).isEqualTo(project.getId());
        assertThat(invocation.payload()).containsEntry("event", "session.created");
        assertThat(invocation.payload().get("data")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("authMethod", "password");
    }

    @Test
    @DisplayName("Self-service password change dispatches password.changed")
    void passwordChangeDispatchesWebhook() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(user.getId().toString())
                                .claim("tenant_id", tenant.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Password123!",
                                "newPassword", "Password456!"
                        ))))
                .andExpect(status().isOk());

        RecordingWebhookDispatchService.Invocation invocation = recordingWebhookDispatchService.awaitSingleInvocation("password.changed");
        assertThat(invocation.projectId()).isEqualTo(project.getId());
        assertThat(invocation.payload().get("subject")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("id", user.getId().toString());
    }

    @Test
    @DisplayName("Admin block, unblock, and delete dispatch user lifecycle webhooks")
    void adminActionsDispatchLifecycleWebhooks() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{userId}/block", user.getId())
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", tenant.getId().toString())
                                .claim("scope", "arkil:admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "fraud review"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/{userId}/unblock", user.getId())
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", tenant.getId().toString())
                                .claim("scope", "arkil:admin"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/admin/users/{userId}", user.getId())
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", tenant.getId().toString())
                                .claim("scope", "arkil:admin"))))
                .andExpect(status().isOk());

        List<String> events = recordingWebhookDispatchService.awaitEvents(3);
        assertThat(events).containsExactlyInAnyOrder("user.blocked", "user.unblocked", "user.deleted");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        RecordingWebhookDispatchService recordingWebhookDispatchService(WebhookRepository webhookRepository, ObjectMapper objectMapper) {
            return new RecordingWebhookDispatchService(webhookRepository, objectMapper);
        }
    }

    static class RecordingWebhookDispatchService extends WebhookDispatchService {
        private final List<Invocation> invocations = new CopyOnWriteArrayList<>();

        RecordingWebhookDispatchService(WebhookRepository webhookRepository, ObjectMapper objectMapper) {
            super(webhookRepository, objectMapper);
        }

        @Override
        public void dispatchEvent(UUID projectId, String eventType, Map<String, Object> payload) {
            invocations.add(new Invocation(projectId, eventType, payload));
        }

        void clear() {
            invocations.clear();
        }

        Invocation awaitSingleInvocation(String expectedEvent) {
            waitFor(() -> invocations.stream().anyMatch(invocation -> expectedEvent.equals(invocation.eventType())));
            return invocations.stream()
                    .filter(invocation -> expectedEvent.equals(invocation.eventType()))
                    .findFirst()
                    .orElseThrow();
        }

        List<String> awaitEvents(int count) {
            waitFor(() -> invocations.size() >= count);
            List<String> events = new ArrayList<>();
            for (Invocation invocation : invocations) {
                events.add(invocation.eventType());
            }
            return events;
        }

        private void waitFor(java.util.function.BooleanSupplier condition) {
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                if (condition.getAsBoolean()) {
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting for webhook invocation", e);
                }
            }
            throw new AssertionError("Timed out waiting for webhook invocation");
        }

        record Invocation(UUID projectId, String eventType, Map<String, Object> payload) {}
    }
}
