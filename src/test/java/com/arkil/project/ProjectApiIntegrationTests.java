package com.arkil.project;

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

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Project CRUD and API Key management.
 * Uses mock JWT tokens for authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectApiIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // The demo bootstrap creates a tenant + admin user; grab their IDs
    private static String createdProjectId;

    // ─────────────────────────────────────────────────────────────────
    // Project CRUD
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/projects — create project")
    void createProject() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                                .claim("scope", "arkil:admin")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Test Project",
                                "slug", "test-project",
                                "description", "Integration test project",
                                "allowedOrigins", List.of("http://localhost:3000"),
                                "redirectUris", List.of("http://localhost:3000/callback")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.project.name").value("Test Project"))
                .andExpect(jsonPath("$.project.slug").value("test-project"))
                .andExpect(jsonPath("$.project.oidcConfig").exists())
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$.secretKey").isString())
                .andReturn();

        // Extract project ID for subsequent tests
        String body = result.getResponse().getContentAsString();
        Map<String, Object> responseMap = objectMapper.readValue(body, Map.class);
        Map<String, Object> project = (Map<String, Object>) responseMap.get("project");
        createdProjectId = (String) project.get("id");
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/v1/projects — duplicate slug rejected")
    void createProjectDuplicateSlug() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                                .claim("scope", "arkil:admin")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Duplicate",
                                "slug", "test-project"
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/projects — list includes created project")
    void listProjects() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.slug == 'test-project')]").exists());
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/projects/{id} — get single project")
    void getProject() throws Exception {
        Assumptions.assumeTrue(createdProjectId != null, "Project must be created first");

        mockMvc.perform(get("/api/v1/projects/{id}", createdProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Project"))
                .andExpect(jsonPath("$.slug").value("test-project"));
    }

    @Test
    @Order(5)
    @DisplayName("PUT /api/v1/projects/{id} — update project")
    void updateProject() throws Exception {
        Assumptions.assumeTrue(createdProjectId != null);

        mockMvc.perform(put("/api/v1/projects/{id}", createdProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                                .claim("scope", "arkil:admin")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated Test Project",
                                "description", "Updated description"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Test Project"));
    }

    // ─────────────────────────────────────────────────────────────────
    // API Key management
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("GET /api/v1/projects/{id}/keys — list keys (including bootstrap key)")
    void listKeys() throws Exception {
        Assumptions.assumeTrue(createdProjectId != null);

        mockMvc.perform(get("/api/v1/projects/{id}/keys", createdProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/v1/projects/{id}/keys — create additional key")
    void createKey() throws Exception {
        Assumptions.assumeTrue(createdProjectId != null);

        mockMvc.perform(post("/api/v1/projects/{id}/keys", createdProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                                .claim("scope", "arkil:admin")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Integration Test Key",
                                "keyType", "TEST"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey.name").value("Integration Test Key"))
                .andExpect(jsonPath("$.secretKey").isString());
    }

    // ─────────────────────────────────────────────────────────────────
    // Soft Delete & Restore
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("DELETE /api/v1/projects/{id} — soft delete project")
    void deleteProject() throws Exception {
        Assumptions.assumeTrue(createdProjectId != null);

        mockMvc.perform(delete("/api/v1/projects/{id}", createdProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Project deleted"));
    }

    @Test
    @Order(21)
    @DisplayName("GET /api/v1/projects/deleted — lists deleted project")
    void listDeletedProjects() throws Exception {
        mockMvc.perform(get("/api/v1/projects/deleted")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.slug == 'test-project')]").exists());
    }

    @Test
    @Order(22)
    @DisplayName("POST /api/v1/projects/{id}/restore — restore deleted project")
    void restoreProject() throws Exception {
        Assumptions.assumeTrue(createdProjectId != null);

        mockMvc.perform(post("/api/v1/projects/{id}/restore", createdProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("00000000-0000-0000-0000-000000000001")
                                .claim("tenant_id", "00000000-0000-0000-0000-000000000001")
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Test Project"));
    }

    // ─────────────────────────────────────────────────────────────────
    // Auth / Access Control
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("GET /api/v1/projects — unauthenticated returns 401")
    void listProjectsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(31)
    @DisplayName("GET /api/v1/projects/{id} — wrong tenant returns not found")
    void getProjectWrongTenant() throws Exception {
        Assumptions.assumeTrue(createdProjectId != null);

        mockMvc.perform(get("/api/v1/projects/{id}", createdProjectId)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("99999999-9999-9999-9999-999999999999")
                                .claim("tenant_id", "99999999-9999-9999-9999-999999999999")
                                .claim("scope", "arkil:admin")
                        )))
                .andExpect(status().is4xxClientError());
    }
}
