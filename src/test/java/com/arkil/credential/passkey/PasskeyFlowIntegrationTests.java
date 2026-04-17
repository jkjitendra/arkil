package com.arkil.credential.passkey;

import com.arkil.client.AuthModule;
import com.arkil.client.ClientAuthPolicy;
import com.arkil.client.ClientAuthPolicyRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasskeyFlowIntegrationTests {

    private static final String CLIENT_ID = "proj_passkey-it";
    private static final String EMAIL = "passkey-it@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ClientAuthPolicyRepository policyRepository;
    @Autowired private PasskeyCredentialRepository passkeyCredentialRepository;

    @BeforeEach
    void setUp() {
        passkeyCredentialRepository.deleteAll();
        policyRepository.deleteAll();

        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("USER").description("User").build()));

        Tenant tenant = tenantRepository.findAll().stream()
                .filter(existing -> "passkey-it".equals(existing.getSlug()))
                .findFirst()
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .slug("passkey-it")
                        .name("Passkey Integration")
                        .enabled(true)
                        .build()));

        userRepository.findByEmail(EMAIL).ifPresentOrElse(user -> {
            user.setEnabled(true);
            user.setEmailVerified(true);
            user.setTenant(tenant);
            user.setRoles(Set.of(userRole));
            userRepository.save(user);
        }, () -> userRepository.save(ArkilUser.builder()
                .tenant(tenant)
                .username("passkey-it")
                .email(EMAIL)
                .displayName("Passkey Test User")
                .enabled(true)
                .emailVerified(true)
                .roles(Set.of(userRole))
                .createdAt(Instant.now())
                .build()));

        policyRepository.save(ClientAuthPolicy.builder()
                .registeredClientInternalId("passkey-it-internal")
                .clientId(CLIENT_ID)
                .enabledModules(Set.of(AuthModule.PASSKEY, AuthModule.EMAIL_PASSWORD))
                .updatedBy("test")
                .build());
    }

    @Test
    @DisplayName("Passkey can be registered, used for hosted login, renamed, and removed")
    void passkeyLifecycle() throws Exception {
        ArkilUser user = userRepository.findByEmail(EMAIL).orElseThrow();
        KeyPair keyPair = generateEcKeyPair();
        String credentialId = encodeBase64Url(randomBytes(32));

        String registrationOptionsJson = mockMvc.perform(post("/api/v1/factors/passkey/register/options")
                        .with(jwt().jwt(jwt -> jwt.subject(user.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowId").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> registrationOptions = objectMapper.readValue(registrationOptionsJson, Map.class);
        String registrationChallenge = (String) registrationOptions.get("challenge");
        String registrationFlowId = (String) registrationOptions.get("flowId");

        byte[] registrationClientData = objectMapper.writeValueAsBytes(Map.of(
                "type", "webauthn.create",
                "challenge", registrationChallenge,
                "origin", "http://localhost:8080"
        ));
        byte[] registrationAuthenticatorData = buildRegistrationAuthenticatorData("localhost", credentialId, 1);

        mockMvc.perform(post("/api/v1/factors/passkey/register")
                        .with(jwt().jwt(jwt -> jwt.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "flowId", registrationFlowId,
                                "label", "MacBook Pro",
                                "credential", Map.of(
                                        "id", credentialId,
                                        "rawId", credentialId,
                                        "type", "public-key",
                                        "response", Map.of(
                                                "clientDataJSON", encodeBase64Url(registrationClientData),
                                                "authenticatorData", encodeBase64Url(registrationAuthenticatorData),
                                                "publicKey", encodeBase64Url(keyPair.getPublic().getEncoded()),
                                                "publicKeyAlgorithm", -7
                                        )
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.passkey.label").value("MacBook Pro"));

        mockMvc.perform(get("/api/v1/factors/passkey")
                        .with(jwt().jwt(jwt -> jwt.subject(user.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passkeys[0].credentialId").value(credentialId));

        String authenticationOptionsJson = mockMvc.perform(post("/webauthn/authenticate/options")
                        .param("client_id", CLIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowId").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> authenticationOptions = objectMapper.readValue(authenticationOptionsJson, Map.class);
        String authenticationChallenge = (String) authenticationOptions.get("challenge");
        String authenticationFlowId = (String) authenticationOptions.get("flowId");

        byte[] authenticationClientData = objectMapper.writeValueAsBytes(Map.of(
                "type", "webauthn.get",
                "challenge", authenticationChallenge,
                "origin", "http://localhost:8080"
        ));
        byte[] authenticationAuthenticatorData = buildAuthenticationAuthenticatorData("localhost", 2);
        byte[] signature = sign(keyPair.getPrivate(), authenticationAuthenticatorData, authenticationClientData);

        mockMvc.perform(post("/webauthn/authenticate")
                        .param("client_id", CLIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "flowId", authenticationFlowId,
                                "credential", Map.of(
                                        "id", credentialId,
                                        "rawId", credentialId,
                                        "type", "public-key",
                                        "response", Map.of(
                                                "clientDataJSON", encodeBase64Url(authenticationClientData),
                                                "authenticatorData", encodeBase64Url(authenticationAuthenticatorData),
                                                "signature", encodeBase64Url(signature)
                                        )
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.email").value(EMAIL));

        mockMvc.perform(patch("/api/v1/factors/passkey/{credentialId}", credentialId)
                        .with(jwt().jwt(jwt -> jwt.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("label", "Desk Security Key"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passkey.label").value("Desk Security Key"));

        mockMvc.perform(delete("/api/v1/factors/passkey/{credentialId}", credentialId)
                        .with(jwt().jwt(jwt -> jwt.subject(user.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Passkey removed"));

        mockMvc.perform(get("/api/v1/factors/passkey")
                        .with(jwt().jwt(jwt -> jwt.subject(user.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passkeys").isEmpty());
    }

    private KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    private byte[] buildRegistrationAuthenticatorData(String rpId, String credentialId, int signCount) throws Exception {
        byte[] rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
        byte flags = (byte) (0x01 | 0x04 | 0x40);
        byte[] counter = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(signCount).array();
        byte[] aaguid = new byte[16];
        byte[] credentialIdBytes = Base64.getUrlDecoder().decode(credentialId);
        byte[] credentialIdLength = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
                .putShort((short) credentialIdBytes.length)
                .array();

        return ByteBuffer.allocate(rpIdHash.length + 1 + counter.length + aaguid.length + credentialIdLength.length + credentialIdBytes.length)
                .put(rpIdHash)
                .put(flags)
                .put(counter)
                .put(aaguid)
                .put(credentialIdLength)
                .put(credentialIdBytes)
                .array();
    }

    private byte[] buildAuthenticationAuthenticatorData(String rpId, int signCount) throws Exception {
        byte[] rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
        byte flags = (byte) (0x01 | 0x04);
        byte[] counter = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(signCount).array();

        return ByteBuffer.allocate(rpIdHash.length + 1 + counter.length)
                .put(rpIdHash)
                .put(flags)
                .put(counter)
                .array();
    }

    private byte[] sign(PrivateKey privateKey, byte[] authenticatorData, byte[] clientDataJson) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(authenticatorData);
        signature.update(sha256(clientDataJson));
        return signature.sign();
    }

    private byte[] sha256(byte[] input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new java.security.SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private String encodeBase64Url(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }
}
