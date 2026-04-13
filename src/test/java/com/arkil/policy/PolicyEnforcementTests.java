package com.arkil.policy;

import com.arkil.client.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Policy Enforcement.
 * Verifies that disabled auth modules are properly blocked by the PolicyEnforcementFilter.
 *
 * Note: OAuth2 authorization paths (/oauth2/authorization/*) are intercepted by Spring Security's
 * OAuth2AuthorizationRequestRedirectFilter before our custom PolicyEnforcementFilter runs,
 * so those are tested separately through the DynamicClientRegistrationRepository behavior.
 * This test focuses on paths where PolicyEnforcementFilter is the primary guard:
 * /webauthn/*, /totp/*, /auth/magic-link*.
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
    @DisplayName("Login page loads without client_id (no error)")
    void loginPageWithoutClientId() throws Exception {
        // Without client_id, the login page still renders (used for OAuth2 auth server flow)
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Arkil")));
    }

    @Test
    @Order(3)
    @DisplayName("FORBIDDEN when accessing WebAuthn without PASSKEY enabled")
    void blockedPasskey() throws Exception {
        mockMvc.perform(post("/webauthn/register/options")
                        .param("client_id", TEST_CLIENT_ID)
                        .param("userId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @DisplayName("FORBIDDEN when accessing TOTP without TOTP enabled")
    void blockedTotp() throws Exception {
        mockMvc.perform(get("/totp/status")
                        .param("client_id", TEST_CLIENT_ID)
                        .param("userId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("Meta API returns all available modules (public endpoint)")
    void metaApiPublic() throws Exception {
        mockMvc.perform(get("/api/v1/meta/auth-modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    @Order(6)
    @DisplayName("Client policy API requires authentication")
    void clientApiRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/clients/" + TEST_CLIENT_ID + "/policy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(7)
    @DisplayName("Policy allows TOTP after enabling it")
    void policyAllowsEnabledModules() throws Exception {
        // Update policy to enable TOTP
        ClientAuthPolicy policy = policyRepository.findByClientId(TEST_CLIENT_ID).orElseThrow();
        policy.setEnabledModules(Set.of(AuthModule.EMAIL_PASSWORD, AuthModule.TOTP));
        policyRepository.save(policy);

        // TOTP endpoint should no longer be blocked by policy filter
        // (it passes the filter and reaches the actual handler, which may return 401 for unauthenticated requests)
        mockMvc.perform(get("/totp/status")
                        .param("client_id", TEST_CLIENT_ID)
                        .param("userId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    @Test
    @Order(8)
    @DisplayName("FORBIDDEN for WebAuthn without any client context")
    void noClientContextBlocked() throws Exception {
        // No client_id param at all - filter blocks with "Missing client context"
        mockMvc.perform(post("/webauthn/register/options")
                        .param("userId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    @DisplayName("FORBIDDEN for unknown client on guarded endpoint")
    void unknownClientBlocked() throws Exception {
        // Unknown client_id on a guarded endpoint (webauthn, not oauth2)
        mockMvc.perform(post("/webauthn/register/options")
                        .param("client_id", "unknown-client")
                        .param("userId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(10)
    @DisplayName("Meta API lists correct number of auth modules")
    void metaApiListsAllModules() throws Exception {
        mockMvc.perform(get("/api/v1/meta/auth-modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(AuthModule.values().length));
    }

    @Test
    @Order(11)
    @DisplayName("Factor API is not blocked without client context for authenticated account settings")
    void factorApiAllowedWithoutClientContext() throws Exception {
        mockMvc.perform(get("/api/v1/factors/totp/status")
                        .with(jwt().jwt(jwt -> jwt.subject("00000000-0000-0000-0000-000000000001"))))
                .andExpect(status().is(org.hamcrest.Matchers.not(403)));
    }

    @Test
    @Order(12)
    @DisplayName("Factor API is blocked when client context disables TOTP")
    void factorApiBlockedForDisabledClientModule() throws Exception {
        mockMvc.perform(get("/api/v1/factors/totp/status")
                        .param("client_id", TEST_CLIENT_ID)
                        .with(jwt().jwt(jwt -> jwt.subject("00000000-0000-0000-0000-000000000001"))))
                .andExpect(status().isForbidden());
    }
}
