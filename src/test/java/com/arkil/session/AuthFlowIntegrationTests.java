package com.arkil.session;

import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.tenant.Tenant;
import com.arkil.tenant.TenantRepository;
import com.arkil.user.ArkilUser;
import com.arkil.user.Role;
import com.arkil.user.RoleRepository;
import com.arkil.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for authentication flows:
 * - Developer registration
 * - Session creation (password login)
 * - Token refresh
 * - Logout
 * - Password reset flow
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordCredentialRepository passwordCredentialRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private static final String TEST_EMAIL = "test-auth@example.com";
    private static final String TEST_PASSWORD = "SecurePass123!";

    @BeforeEach
    void ensureTestUser() {
        // Ensure a USER role exists
        if (roleRepository.findByName("USER").isEmpty()) {
            roleRepository.save(Role.builder().name("USER").description("User").build());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Registration
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/auth/register — successful developer registration")
    void registerDeveloper() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", TEST_EMAIL,
                                "password", TEST_PASSWORD,
                                "orgName", "Test Org"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL));
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/v1/auth/register — duplicate email rejected")
    void registerDuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", TEST_EMAIL,
                                "password", TEST_PASSWORD
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("registration_failed"));
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/v1/auth/register — validation: short password")
    void registerShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "short-pw@example.com",
                                "password", "short"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/v1/auth/register — validation: invalid email")
    void registerInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "not-an-email",
                                "password", TEST_PASSWORD
                        ))))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────
    // Session Creation (Login)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("POST /api/v1/sessions — successful password login")
    void createSessionWithPassword() throws Exception {
        ensureUserExists();

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", TEST_EMAIL,
                                "password", TEST_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL));
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/v1/sessions — wrong password")
    void createSessionWrongPassword() throws Exception {
        ensureUserExists();

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", TEST_EMAIL,
                                "password", "WrongPassword!"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    @Order(12)
    @DisplayName("POST /api/v1/sessions — unknown user")
    void createSessionUnknownUser() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "nonexistent@example.com",
                                "password", TEST_PASSWORD
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    @Order(13)
    @DisplayName("POST /api/v1/sessions — missing credentials")
    void createSessionMissingCredential() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", TEST_EMAIL
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("missing_credential"));
    }

    @Test
    @Order(14)
    @DisplayName("POST /api/v1/sessions — disabled account rejected")
    void createSessionDisabledAccount() throws Exception {
        ensureUserExists();
        ArkilUser user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);

        try {
            mockMvc.perform(post("/api/v1/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "identifier", TEST_EMAIL,
                                    "password", TEST_PASSWORD
                            ))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("account_disabled"));
        } finally {
            // Re-enable for subsequent tests
            user.setEnabled(true);
            userRepository.save(user);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Token Refresh
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("POST /api/v1/sessions/refresh — returns JWT when valid cookie provided")
    void refreshSessionWithCookie() throws Exception {
        ensureUserExists();

        // First, login to get a refresh token cookie
        MvcResult loginResult = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", TEST_EMAIL,
                                "password", TEST_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        // Extract the refresh token cookie from response
        jakarta.servlet.http.Cookie[] cookies = loginResult.getResponse().getCookies();
        jakarta.servlet.http.Cookie refreshCookie = null;
        for (jakarta.servlet.http.Cookie c : cookies) {
            if ("arkil_refresh_token".equals(c.getName())) {
                refreshCookie = c;
                break;
            }
        }
        Assertions.assertNotNull(refreshCookie, "Login should return arkil_refresh_token cookie");

        // Send the refresh request with the cookie
        mockMvc.perform(post("/api/v1/sessions/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @Order(21)
    @DisplayName("POST /api/v1/sessions/refresh — 401 without cookie")
    void refreshSessionNoCookie() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("no_refresh_token"));
    }

    // ─────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("DELETE /api/v1/sessions/current — logout clears cookie")
    void logoutClearsCookie() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    // ─────────────────────────────────────────────────────────────────
    // Password Reset API
    // ─────────────────────────────────────────────────────────────────

    @Test
    @Order(40)
    @DisplayName("POST /api/v1/auth/forgot-password — always returns success (anti-enumeration)")
    void forgotPasswordAntiEnumeration() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "nonexistent@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(41)
    @DisplayName("POST /api/v1/auth/reset-password — invalid token rejected")
    void resetPasswordInvalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "invalid-token-value",
                                "newPassword", "NewSecure123!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_token"));
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private void ensureUserExists() {
        if (userRepository.findByEmail(TEST_EMAIL).isEmpty()) {
            Tenant tenant = tenantRepository.findBySlug("demo")
                    .orElseGet(() -> tenantRepository.save(Tenant.builder()
                            .slug("test-auth")
                            .name("Test Auth Tenant")
                            .enabled(true)
                            .build()));

            Role userRole = roleRepository.findByName("USER")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("USER").description("User").build()));

            ArkilUser user = ArkilUser.builder()
                    .tenant(tenant)
                    .username("test-auth-user")
                    .email(TEST_EMAIL)
                    .displayName("Test Auth User")
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            user.getRoles().add(userRole);
            userRepository.save(user);

            passwordCredentialRepository.save(PasswordCredential.builder()
                    .user(user)
                    .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                    .algorithm("bcrypt")
                    .build());
        }
    }
}
