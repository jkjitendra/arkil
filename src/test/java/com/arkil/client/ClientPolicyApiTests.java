package com.arkil.client;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for Client Policy API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClientPolicyApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientAuthPolicyRepository policyRepository;

    @BeforeEach
    void setup() {
        policyRepository.deleteAll();

        ClientAuthPolicy policy = ClientAuthPolicy.builder()
                .registeredClientInternalId("api-test-internal")
                .clientId("api-test-client")
                .enabledModules(Set.of(AuthModule.EMAIL_PASSWORD, AuthModule.OAUTH2_GOOGLE))
                .updatedBy("test")
                .build();

        policyRepository.save(policy);
    }

    @Test
    @DisplayName("Meta API lists all auth modules")
    void metaApiListsModules() throws Exception {
        mockMvc.perform(get("/api/v1/meta/auth-modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(AuthModule.values().length))
                .andExpect(jsonPath("$[?(@.name == 'EMAIL_PASSWORD')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'OAUTH2_GOOGLE')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'PASSKEY')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'TOTP')]").exists());
    }

    @Test
    @DisplayName("Auth module DTO has all required fields")
    void authModuleDtoFields() throws Exception {
        mockMvc.perform(get("/api/v1/meta/auth-modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].displayName").exists())
                .andExpect(jsonPath("$[0].description").exists());
    }

    @Test
    @DisplayName("Client policy endpoint requires authentication")
    void policyRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/clients/api-test-client/policy"))
                .andExpect(status().isUnauthorized());
    }
}
