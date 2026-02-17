package com.arkil.webhook;

import com.arkil.project.Project;
import com.arkil.project.ProjectRepository;
import com.arkil.tenant.Tenant;
import com.arkil.tenant.TenantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Webhook CRUD API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebhookApiIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TenantRepository tenantRepository;

    private static String testProjectId;
    private static String testTenantId;
    private static String createdWebhookId;

    @BeforeAll
    static void resolveProjectId(@Autowired ProjectRepository projectRepository,
                                  @Autowired TenantRepository tenantRepository) {
        // Use the demo project created by DemoDataBootstrap
        Project demoProject = projectRepository.findBySlug("demo-app").orElse(null);
        if (demoProject != null) {
            testProjectId = demoProject.getId().toString();
            testTenantId = demoProject.getTenantId().toString();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/projects/{id}/webhooks — create webhook")
    void createWebhook() throws Exception {
        Assumptions.assumeTrue(testProjectId != null, "Demo project must exist");

        MvcResult result = mockMvc.perform(post("/api/v1/projects/{id}/webhooks", testProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", testTenantId)
                                .claim("scope", "arkil:admin")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", "https://httpbin.org/post",
                                "events", List.of("user.created", "session.created"),
                                "description", "Test webhook"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.url").value("https://httpbin.org/post"))
                .andExpect(jsonPath("$.signingSecret").isString())
                .andExpect(jsonPath("$.signingSecret", startsWith("whsec_")))
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(body, Map.class);
        createdWebhookId = (String) responseMap.get("id");
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/projects/{id}/webhooks — list includes created webhook")
    void listWebhooks() throws Exception {
        Assumptions.assumeTrue(testProjectId != null);

        mockMvc.perform(get("/api/v1/projects/{id}/webhooks", testProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", testTenantId)
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(3)
    @DisplayName("PUT /api/v1/projects/{id}/webhooks/{wid} — update webhook")
    void updateWebhook() throws Exception {
        Assumptions.assumeTrue(createdWebhookId != null);

        mockMvc.perform(put("/api/v1/projects/{id}/webhooks/{wid}", testProjectId, createdWebhookId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", testTenantId)
                                .claim("scope", "arkil:admin")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Updated description",
                                "enabled", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/v1/projects/{id}/webhooks/{wid}/test — test ping")
    void testWebhookPing() throws Exception {
        Assumptions.assumeTrue(createdWebhookId != null);

        // Re-enable for ping test
        mockMvc.perform(put("/api/v1/projects/{id}/webhooks/{wid}", testProjectId, createdWebhookId)
                .with(jwt().jwt(jwt -> jwt
                        .subject("00000000-0000-0000-0000-000000000001")
                        .claim("tenant_id", testTenantId)
                        .claim("scope", "arkil:admin")
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", true))));

        // The ping may fail since httpbin might not be reachable, but endpoint should work
        mockMvc.perform(post("/api/v1/projects/{id}/webhooks/{wid}/test", testProjectId, createdWebhookId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", testTenantId)
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").isBoolean())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/v1/projects/{id}/webhooks/events — list supported events")
    void listSupportedEvents() throws Exception {
        Assumptions.assumeTrue(testProjectId != null);

        mockMvc.perform(get("/api/v1/projects/{id}/webhooks/events", testProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", testTenantId)
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasItem("user.created")));
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/v1/projects/{id}/webhooks — invalid event type rejected")
    void createWebhookInvalidEvent() throws Exception {
        Assumptions.assumeTrue(testProjectId != null);

        mockMvc.perform(post("/api/v1/projects/{id}/webhooks", testProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", testTenantId)
                                .claim("scope", "arkil:admin")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", "https://example.com/hooks",
                                "events", List.of("invalid.event.type")
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_event"));
    }

    @Test
    @Order(10)
    @DisplayName("DELETE /api/v1/projects/{id}/webhooks/{wid} — delete webhook")
    void deleteWebhook() throws Exception {
        Assumptions.assumeTrue(createdWebhookId != null);

        mockMvc.perform(delete("/api/v1/projects/{id}/webhooks/{wid}", testProjectId, createdWebhookId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", testTenantId)
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().isNoContent());
    }

    // ─────────────────────────────────────────────────────────────────
    // Access Control
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("GET /api/v1/projects/{id}/webhooks — unauthenticated returns 401")
    void listWebhooksUnauthenticated() throws Exception {
        Assumptions.assumeTrue(testProjectId != null);

        mockMvc.perform(get("/api/v1/projects/{id}/webhooks", testProjectId))
                .andExpect(status().isUnauthorized());
    }
}
