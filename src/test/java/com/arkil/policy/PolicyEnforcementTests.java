package com.arkil.policy;

import com.arkil.client.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Policy Enforcement.
 * Verifies that disabled auth modules are properly blocked at all layers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PolicyEnforcementTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientAuthPolicyRepository policyRepository;

    private static final String TEST_CLIENT_ID = "test-client";

    @BeforeEach
    void setup() {
        // Clean up and create test policy
        policyRepository.deleteAll();

        ClientAuthPolicy policy = ClientAuthPolicy.builder()
                .registeredClientInternalId("test-internal")
                .clientId(TEST_CLIENT_ID)
                .enabledModules(Set.of(AuthModule.EMAIL_PASSWORD)) // Only password enabled
                .updatedBy("test")
                .build();

        policyRepository.save(policy);
    }

    @Test
    @Order(1)
    @DisplayName("Login page loads with client_id")
    void loginPageLoads() throws Exception {
        mockMvc.perform(get("/login")
                        .param("client_id", TEST_CLIENT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Arkil")));
    }

    @Test
    @Order(2)
    @DisplayName("Login page shows warning without client_id")
    void loginPageWithoutClientId() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Missing")));
    }

    @Test
    @Order(3)
    @DisplayName("FORBIDDEN when accessing disabled OAuth2 provider (Google)")
    void blockedGoogleOAuth() throws Exception {
        // Google OAuth is disabled for test-client
        mockMvc.perform(get("/oauth2/authorization/google")
                        .param("client_id", TEST_CLIENT_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not enabled")));
    }

    @Test
    @Order(4)
    @DisplayName("FORBIDDEN when accessing disabled OAuth2 provider (GitHub)")
    void blockedGitHubOAuth() throws Exception {
        // GitHub OAuth is disabled for test-client
        mockMvc.perform(get("/oauth2/authorization/github")
                        .param("client_id", TEST_CLIENT_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not enabled")));
    }

    @Test
    @Order(5)
    @DisplayName("FORBIDDEN when accessing WebAuthn without PASSKEY enabled")
    void blockedPasskey() throws Exception {
        mockMvc.perform(post("/webauthn/register/options")
                        .param("client_id", TEST_CLIENT_ID)
                        .param("userId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @DisplayName("FORBIDDEN when accessing TOTP without TOTP enabled")
    void blockedTotp() throws Exception {
        mockMvc.perform(get("/totp/status")
                        .param("client_id", TEST_CLIENT_ID)
                        .param("userId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    @DisplayName("Meta API returns all available modules (public endpoint)")
    void metaApiPublic() throws Exception {
        mockMvc.perform(get("/api/v1/meta/auth-modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    @Order(8)
    @DisplayName("Client policy API requires authentication")
    void clientApiRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/clients/" + TEST_CLIENT_ID + "/policy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    @DisplayName("Policy allows enabled modules when updated")
    void policyAllowsEnabledModules() throws Exception {
        // Update policy to enable TOTP
        ClientAuthPolicy policy = policyRepository.findByClientId(TEST_CLIENT_ID).orElseThrow();
        policy.setEnabledModules(Set.of(AuthModule.EMAIL_PASSWORD, AuthModule.TOTP));
        policyRepository.save(policy);

        // Now TOTP endpoint should work
        mockMvc.perform(get("/totp/status")
                        .param("client_id", TEST_CLIENT_ID)
                        .param("userId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(10)
    @DisplayName("Deny-by-default: unknown client blocked")
    void unknownClientBlocked() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google")
                        .param("client_id", "unknown-client"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client")));
    }
}
