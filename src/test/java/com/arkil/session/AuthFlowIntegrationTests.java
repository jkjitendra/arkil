package com.arkil.session;

import com.arkil.credential.totp.TotpCredentialRepository;
import com.arkil.credential.totp.TotpService;
import com.arkil.credential.password.PasswordCredential;
import com.arkil.credential.password.PasswordCredentialRepository;
import com.arkil.auth.AuthSessionAttributes;
import com.arkil.email.EmailToken;
import com.arkil.email.EmailTokenRepository;
import com.arkil.security.SecretEncryptionService;
import com.arkil.project.Project;
import com.arkil.project.ProjectRepository;
import com.arkil.project.RegisteredClientBridgeService;
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
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordCredentialRepository passwordCredentialRepository;
    @Autowired private EmailTokenRepository emailTokenRepository;
    @Autowired private TotpService totpService;
    @Autowired private TotpCredentialRepository totpCredentialRepository;
    @Autowired private SecretEncryptionService secretEncryptionService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private RegisteredClientBridgeService registeredClientBridgeService;

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
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.orgName").doesNotExist());
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

    @Test
    @Order(5)
    @DisplayName("Developer registration rejects markup in workspace names")
    void registerRejectsMarkup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "xss-check@example.com",
                                "password", TEST_PASSWORD,
                                "orgName", "<script>alert(1)</script>"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    @Order(6)
    @DisplayName("Social return URL rejects another origin despite forged Host header")
    void socialReturnRejectsExternalOrigin() throws Exception {
        mockMvc.perform(get("/auth/social/google")
                        .header("Host", "evil.example")
                        .param("return_to", "https://evil.example/oauth2/authorize?client_id=arkil-dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=oauth2"));
    }

    @Test
    @Order(7)
    @DisplayName("Social return URL uses configured issuer for relative authorization paths")
    void socialReturnUsesConfiguredIssuer() throws Exception {
        mockMvc.perform(get("/auth/social/google")
                        .header("Host", "evil.example")
                        .param("return_to", "/oauth2/authorize?client_id=arkil-dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(request().sessionAttribute(AuthSessionAttributes.SOCIAL_LOGIN_RETURN_TO,
                        "http://localhost:8080/oauth2/authorize?client_id=arkil-dashboard"));
    }

    @Test
    @Order(8)
    @DisplayName("Hosted login resolves the project from a saved OAuth authorization request")
    void oauthAuthorizationRedirectKeepsProjectContext() throws Exception {
        ensureIsolatedProject();
        MvcResult authorization = mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "proj_auth-flow-isolation")
                        .queryParam("redirect_uri", "http://localhost:5173/callback")
                        .queryParam("scope", "openid")
                        .queryParam("state", "test-state")
                        .queryParam("code_challenge", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        mockMvc.perform(get("/login")
                        .session((MockHttpSession) authorization.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("clientContext",
                        hasProperty("clientId", is("proj_auth-flow-isolation"))));
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
    @Order(12)
    @DisplayName("Direct session rejects a caller-supplied unknown client")
    void createSessionUnknownClient() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", TEST_EMAIL,
                                "password", TEST_PASSWORD,
                                "clientId", "proj_does-not-exist"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    @Order(12)
    @DisplayName("Direct session cannot select a user from another tenant")
    void createSessionRejectsCrossTenantUser() throws Exception {
        ensureUserExists();
        ensureIsolatedProject();
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", TEST_EMAIL,
                                "password", TEST_PASSWORD,
                                "clientId", "proj_auth-flow-isolation"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    @Order(12)
    @DisplayName("Direct session enforces the selected project's auth policy")
    void createSessionRejectsDisabledMethod() throws Exception {
        ensureIsolatedProject();
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "demo@arkil.local",
                                "magicLinkToken", "untrusted-token",
                                "clientId", "proj_auth-flow-isolation"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("auth_method_disabled"));
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

    @Test
    @Order(15)
    @DisplayName("POST /api/v1/sessions — unverified email rejected with resend hint")
    void createSessionUnverifiedEmailRejected() throws Exception {
        ensureUserExists();
        ArkilUser user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        user.setEmailVerified(false);
        userRepository.save(user);

        try {
            mockMvc.perform(post("/api/v1/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "identifier", TEST_EMAIL,
                                    "password", TEST_PASSWORD
                            ))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("email_not_verified"))
                    .andExpect(jsonPath("$.email").value(TEST_EMAIL))
                    .andExpect(jsonPath("$.canResendVerification").value(true));
        } finally {
            user.setEmailVerified(true);
            userRepository.save(user);
        }
    }

    @Test
    @Order(16)
    @DisplayName("GET /auth/magic-link/verify — unverified email shows resend verification action")
    void hostedMagicLinkVerifyRequiresVerifiedEmail() throws Exception {
        ensureUserExists();
        ArkilUser user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
        user.setEmailVerified(false);
        userRepository.save(user);

        EmailToken token = emailTokenRepository.save(EmailToken.builder()
                .token("hosted-magic-token")
                .userId(user.getId())
                .email(user.getEmail())
                .type(EmailToken.TokenType.MAGIC_LINK)
                .expiresAt(java.time.Instant.now().plusSeconds(900))
                .build());

        try {
            mockMvc.perform(get("/auth/magic-link/verify").param("token", token.getToken()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Verify your email before signing in with a magic link.")))
                    .andExpect(content().string(containsString("Resend Verification Email")));
        } finally {
            user.setEmailVerified(true);
            userRepository.save(user);
        }
    }

    @Test
    @Order(17)
    @DisplayName("POST /login — hosted password login redirects for TOTP challenge")
    void hostedPasswordLoginRequiresTotp() throws Exception {
        ensureUserExists();
        enableTotpForTestUser();
        ArkilUser user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();

        try {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", TEST_EMAIL)
                            .param("password", TEST_PASSWORD))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("/login?error=mfa_required*"));
        } finally {
            totpService.remove(user.getId());
        }
    }

    @Test
    @Order(18)
    @DisplayName("POST /login — hosted password login succeeds with valid TOTP")
    void hostedPasswordLoginSucceedsWithTotp() throws Exception {
        ensureUserExists();
        String code = enableTotpForTestUser();
        ArkilUser user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();

        try {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("username", TEST_EMAIL)
                            .param("password", TEST_PASSWORD)
                            .param("totpCode", code))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"));
        } finally {
            totpService.remove(user.getId());
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
        ArkilUser user = userRepository.findByEmail(TEST_EMAIL).orElseGet(() -> {
            Tenant tenant = tenantRepository.findBySlug("demo")
                    .orElseGet(() -> tenantRepository.save(Tenant.builder()
                            .slug("test-auth")
                            .name("Test Auth Tenant")
                            .enabled(true)
                            .build()));

            Role userRole = roleRepository.findByName("USER")
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name("USER").description("User").build()));

            ArkilUser createdUser = ArkilUser.builder()
                    .tenant(tenant)
                    .username("test-auth-user")
                    .email(TEST_EMAIL)
                    .displayName("Test Auth User")
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            createdUser.getRoles().add(userRole);
            return userRepository.save(createdUser);
        });

        user.setEnabled(true);
        user.setEmailVerified(true);
        userRepository.save(user);

        PasswordCredential credential = passwordCredentialRepository.findByUser_Id(user.getId())
                .orElseGet(() -> PasswordCredential.builder()
                        .user(user)
                        .algorithm("bcrypt")
                        .build());
        credential.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        passwordCredentialRepository.save(credential);
    }

    private void ensureIsolatedProject() {
        Tenant tenant = tenantRepository.findBySlug("auth-flow-isolation")
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .slug("auth-flow-isolation")
                        .name("Auth Flow Isolation")
                        .enabled(true)
                        .build()));
        projectRepository.findBySlug("auth-flow-isolation")
                .orElseGet(() -> {
                    Project project = projectRepository.save(Project.builder()
                            .name("Auth Flow Isolation")
                            .slug("auth-flow-isolation")
                            .tenantId(tenant.getId())
                            .ownerId(UUID.randomUUID())
                            .active(true)
                            .build());
                    project.setRegisteredClientId(registeredClientBridgeService.createRegisteredClientForProject(project));
                    return projectRepository.save(project);
                });
    }

    private String enableTotpForTestUser() {
        ArkilUser user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();

        if (!totpService.isEnabled(user.getId())) {
            TotpService.TotpEnrollmentResponse enrollment = totpService.startEnrollment(user.getId(), "Arkil");
            String code = generateTotpCode(enrollment.secret(), "SHA1", 6, 30);
            Assertions.assertTrue(totpService.confirmEnrollment(user.getId(), code));
        }

        String secret = totpCredentialRepository.findByUserId(user.getId())
                .map(credential -> secretEncryptionService.decrypt(credential.getSecretEncrypted()))
                .orElseThrow();

        return generateTotpCode(secret, "SHA1", 6, 30);
    }

    private String generateTotpCode(String secretBase32, String algorithm, int digits, int period) {
        byte[] secret = decodeBase32(secretBase32);
        long timeStep = java.time.Instant.now().getEpochSecond() / period;
        return generateCode(secret, timeStep, algorithm, digits);
    }

    private byte[] decodeBase32(String encoded) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.util.List<Byte> bytes = new java.util.ArrayList<>();
        int buffer = 0;
        int bitsInBuffer = 0;

        for (char c : encoded.toUpperCase().toCharArray()) {
            int value = alphabet.indexOf(c);
            if (value >= 0) {
                buffer = (buffer << 5) | value;
                bitsInBuffer += 5;
                if (bitsInBuffer >= 8) {
                    bytes.add((byte) (buffer >> (bitsInBuffer - 8)));
                    bitsInBuffer -= 8;
                }
            }
        }

        byte[] result = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            result[i] = bytes.get(i);
        }
        return result;
    }

    private String generateCode(byte[] secret, long timeStep, String algorithm, int digits) {
        try {
            byte[] timeBytes = java.nio.ByteBuffer.allocate(8).putLong(timeStep).array();
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("Hmac" + algorithm);
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "RAW"));
            byte[] hash = mac.doFinal(timeBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
