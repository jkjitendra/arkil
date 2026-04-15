package com.arkil.credential.passkey;

import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasskeyService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final PasskeyCredentialRepository passkeyCredentialRepository;
    private final PasskeyFlowService passkeyFlowService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${arkil.email.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${arkil.webauthn.rp-name:Arkil}")
    private String configuredRpName;

    @Value("${arkil.webauthn.rp-id:}")
    private String configuredRpId;

    @Value("${arkil.webauthn.origin:}")
    private String configuredOrigin;

    @Value("${arkil.webauthn.timeout-ms:60000}")
    private long timeoutMs;

    private String rpId;
    private String origin;

    @PostConstruct
    void initialize() {
        URI baseUri = URI.create(baseUrl);
        this.rpId = isBlank(configuredRpId) ? baseUri.getHost() : configuredRpId.trim();
        this.origin = isBlank(configuredOrigin)
                ? buildOrigin(baseUri)
                : buildOrigin(URI.create(configuredOrigin));

        if (isBlank(rpId) || isBlank(origin)) {
            throw new IllegalStateException("WebAuthn RP configuration is invalid");
        }
    }

    public RegistrationOptions issueRegistrationOptions(ArkilUser user) {
        PasskeyFlowState flow = passkeyFlowService.create(PasskeyFlowType.REGISTRATION, user.getId(), rpId, origin);

        List<Map<String, Object>> excludeCredentials = passkeyCredentialRepository.findByUserIdAndRpId(user.getId(), rpId)
                .stream()
                .map(credential -> Map.<String, Object>of(
                        "type", "public-key",
                        "id", credential.getCredentialId()
                ))
                .toList();

        Map<String, Object> options = Map.of(
                "flowId", flow.flowId(),
                "challenge", flow.challenge(),
                "rp", Map.of(
                        "name", configuredRpName,
                        "id", rpId
                ),
                "user", Map.of(
                        "id", encodeUuid(user.getId()),
                        "name", user.getEmail(),
                        "displayName", displayNameFor(user)
                ),
                "pubKeyCredParams", List.of(
                        Map.of("type", "public-key", "alg", -7),
                        Map.of("type", "public-key", "alg", -257)
                ),
                "timeout", timeoutMs,
                "attestation", "none",
                "authenticatorSelection", Map.of(
                        "residentKey", "preferred",
                        "userVerification", "preferred"
                ),
                "excludeCredentials", excludeCredentials
        );

        return new RegistrationOptions(flow, options);
    }

    public PasskeyCredential completeRegistration(ArkilUser user, Map<String, Object> credentialPayload, String label) {
        String flowId = asText(credentialPayload.get("flowId"));
        PasskeyFlowState flow = passkeyFlowService.consume(flowId, PasskeyFlowType.REGISTRATION);

        if (!user.getId().equals(flow.userId())) {
            throw new PasskeyValidationException("Passkey registration session does not match the signed-in user.");
        }

        Map<String, Object> credential = nestedMap(credentialPayload, "credential");
        Map<String, Object> response = nestedMap(credential, "response");

        String credentialId = firstNonBlank(asText(credential.get("rawId")), asText(credential.get("id")));
        if (isBlank(credentialId)) {
            throw new PasskeyValidationException("Passkey credential ID is missing.");
        }
        if (passkeyCredentialRepository.existsByCredentialId(credentialId)) {
            throw new PasskeyValidationException("This passkey is already registered.");
        }

        byte[] clientDataJson = decodeBase64Url(asText(response.get("clientDataJSON")), "clientDataJSON");
        byte[] authenticatorData = decodeBase64Url(asText(response.get("authenticatorData")), "authenticatorData");
        byte[] publicKeyBytes = decodeBase64Url(asText(response.get("publicKey")), "publicKey");
        Integer publicKeyAlgorithm = asInteger(response.get("publicKeyAlgorithm"));

        validateClientData(clientDataJson, "webauthn.create", flow.challenge(), flow.origin());
        AuthenticatorData parsedAuthenticatorData = parseAuthenticatorData(authenticatorData);
        validateRpIdHash(parsedAuthenticatorData.rpIdHash(), flow.rpId());

        if (!parsedAuthenticatorData.userPresent()) {
            throw new PasskeyValidationException("Authenticator did not confirm user presence.");
        }
        if (!parsedAuthenticatorData.attestedCredentialDataIncluded()) {
            throw new PasskeyValidationException("Authenticator response did not include attested credential data.");
        }

        String attestedCredentialId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(parsedAuthenticatorData.attestedCredentialId());
        if (!attestedCredentialId.equals(credentialId)) {
            throw new PasskeyValidationException("Authenticator credential ID did not match the browser response.");
        }

        ensureSupportedAlgorithm(publicKeyAlgorithm);

        PasskeyCredential credentialEntity = passkeyCredentialRepository.save(PasskeyCredential.builder()
                .user(user)
                .credentialId(credentialId)
                .publicKey(Base64.getUrlEncoder().withoutPadding().encodeToString(publicKeyBytes))
                .publicKeyAlgorithm(publicKeyAlgorithm)
                .signCount((long) parsedAuthenticatorData.signCount())
                .label(defaultLabel(label))
                .rpId(flow.rpId())
                .aaguid(parsedAuthenticatorData.aaguid())
                .userVerificationCapable(parsedAuthenticatorData.userVerified())
                .discoverable(true)
                .createdAt(Instant.now())
                .lastUsedAt(Instant.now())
                .build());

        return credentialEntity;
    }

    public AuthenticationOptions issueAuthenticationOptions(UUID userId) {
        PasskeyFlowState flow = passkeyFlowService.create(PasskeyFlowType.AUTHENTICATION, userId, rpId, origin);

        List<Map<String, Object>> allowCredentials = userId != null
                ? passkeyCredentialRepository.findByUserIdAndRpId(userId, rpId).stream()
                .map(credential -> Map.<String, Object>of(
                        "type", "public-key",
                        "id", credential.getCredentialId()
                ))
                .toList()
                : List.of();

        Map<String, Object> options = Map.of(
                "flowId", flow.flowId(),
                "challenge", flow.challenge(),
                "timeout", timeoutMs,
                "rpId", rpId,
                "userVerification", "preferred",
                "allowCredentials", allowCredentials
        );

        return new AuthenticationOptions(flow, options);
    }

    public AuthenticationResult completeAuthentication(Map<String, Object> assertionPayload) {
        String flowId = asText(assertionPayload.get("flowId"));
        PasskeyFlowState flow = passkeyFlowService.consume(flowId, PasskeyFlowType.AUTHENTICATION);

        Map<String, Object> credential = nestedMap(assertionPayload, "credential");
        Map<String, Object> response = nestedMap(credential, "response");

        String credentialId = firstNonBlank(asText(credential.get("rawId")), asText(credential.get("id")));
        if (isBlank(credentialId)) {
            throw new PasskeyValidationException("Passkey credential ID is missing.");
        }

        PasskeyCredential storedCredential = passkeyCredentialRepository.findByCredentialId(credentialId)
                .orElseThrow(() -> new PasskeyValidationException("Passkey not recognized."));

        if (!storedCredential.getRpId().equals(flow.rpId())) {
            throw new PasskeyValidationException("Passkey RP ID does not match this login request.");
        }
        if (flow.userId() != null && !flow.userId().equals(storedCredential.getUser().getId())) {
            throw new PasskeyValidationException("Passkey does not match the expected account.");
        }

        byte[] clientDataJson = decodeBase64Url(asText(response.get("clientDataJSON")), "clientDataJSON");
        byte[] authenticatorData = decodeBase64Url(asText(response.get("authenticatorData")), "authenticatorData");
        byte[] signature = decodeBase64Url(asText(response.get("signature")), "signature");

        validateClientData(clientDataJson, "webauthn.get", flow.challenge(), flow.origin());
        AuthenticatorData parsedAuthenticatorData = parseAuthenticatorData(authenticatorData);
        validateRpIdHash(parsedAuthenticatorData.rpIdHash(), flow.rpId());

        if (!parsedAuthenticatorData.userPresent()) {
            throw new PasskeyValidationException("Authenticator did not confirm user presence.");
        }

        verifySignature(storedCredential, authenticatorData, clientDataJson, signature);

        if (storedCredential.getSignCount() != null
                && storedCredential.getSignCount() > 0
                && parsedAuthenticatorData.signCount() > 0
                && parsedAuthenticatorData.signCount() <= storedCredential.getSignCount()) {
            throw new PasskeyValidationException("Passkey sign counter did not advance.");
        }

        storedCredential.setSignCount((long) parsedAuthenticatorData.signCount());
        storedCredential.setLastUsedAt(Instant.now());
        PasskeyCredential savedCredential = passkeyCredentialRepository.save(storedCredential);

        UUID userId = savedCredential.getUser().getId();
        ArkilUser user = userRepository.findById(userId)
                .orElseThrow(() -> new PasskeyValidationException("Passkey user was not found."));

        return new AuthenticationResult(user, savedCredential);
    }

    public Map<String, Object> passkeySummary(PasskeyCredential credential) {
        return Map.of(
                "id", credential.getId().toString(),
                "credentialId", credential.getCredentialId(),
                "label", defaultLabel(credential.getLabel()),
                "createdAt", credential.getCreatedAt().toString(),
                "lastUsedAt", credential.getLastUsedAt() != null ? credential.getLastUsedAt().toString() : "",
                "rpId", credential.getRpId(),
                "userVerificationCapable", Boolean.TRUE.equals(credential.getUserVerificationCapable())
        );
    }

    public String getOrigin() {
        return origin;
    }

    public String getRpId() {
        return rpId;
    }

    private void validateClientData(byte[] clientDataJson, String expectedType, String expectedChallenge, String expectedOrigin) {
        try {
            Map<String, Object> clientData = objectMapper.readValue(clientDataJson, MAP_TYPE);
            if (!expectedType.equals(clientData.get("type"))) {
                throw new PasskeyValidationException("Unexpected WebAuthn ceremony type.");
            }
            if (!expectedChallenge.equals(clientData.get("challenge"))) {
                throw new PasskeyValidationException("Passkey challenge did not match.");
            }
            if (!expectedOrigin.equals(clientData.get("origin"))) {
                throw new PasskeyValidationException("Passkey origin did not match the relying party.");
            }
        } catch (PasskeyValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new PasskeyValidationException("Unable to read the browser passkey response.");
        }
    }

    private void validateRpIdHash(byte[] actualHash, String expectedRpId) {
        try {
            byte[] expectedHash = MessageDigest.getInstance("SHA-256")
                    .digest(expectedRpId.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expectedHash, actualHash)) {
                throw new PasskeyValidationException("Passkey RP ID did not match this application.");
            }
        } catch (PasskeyValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new PasskeyValidationException("Unable to verify the passkey RP ID.");
        }
    }

    private void verifySignature(PasskeyCredential storedCredential, byte[] authenticatorData, byte[] clientDataJson, byte[] signature) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] clientDataHash = digest.digest(clientDataJson);
            byte[] signedBytes = ByteBuffer.allocate(authenticatorData.length + clientDataHash.length)
                    .put(authenticatorData)
                    .put(clientDataHash)
                    .array();

            PublicKey publicKey = decodePublicKey(storedCredential);
            Signature verifier = Signature.getInstance(signatureAlgorithm(storedCredential.getPublicKeyAlgorithm()));
            verifier.initVerify(publicKey);
            verifier.update(signedBytes);
            if (!verifier.verify(signature)) {
                throw new PasskeyValidationException("Passkey signature verification failed.");
            }
        } catch (PasskeyValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new PasskeyValidationException("Unable to verify the passkey assertion.");
        }
    }

    private PublicKey decodePublicKey(PasskeyCredential credential) throws Exception {
        byte[] publicKeyBytes = Base64.getUrlDecoder().decode(credential.getPublicKey());
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        return switch (credential.getPublicKeyAlgorithm()) {
            case -7 -> KeyFactory.getInstance("EC").generatePublic(keySpec);
            case -257 -> KeyFactory.getInstance("RSA").generatePublic(keySpec);
            default -> throw new PasskeyValidationException("Unsupported passkey algorithm.");
        };
    }

    private String signatureAlgorithm(Integer coseAlgorithm) {
        ensureSupportedAlgorithm(coseAlgorithm);
        return switch (coseAlgorithm) {
            case -7 -> "SHA256withECDSA";
            case -257 -> "SHA256withRSA";
            default -> throw new PasskeyValidationException("Unsupported passkey algorithm.");
        };
    }

    private void ensureSupportedAlgorithm(Integer coseAlgorithm) {
        if (coseAlgorithm == null || (coseAlgorithm != -7 && coseAlgorithm != -257)) {
            throw new PasskeyValidationException("Unsupported passkey algorithm.");
        }
    }

    private AuthenticatorData parseAuthenticatorData(byte[] authenticatorData) {
        if (authenticatorData == null || authenticatorData.length < 37) {
            throw new PasskeyValidationException("Authenticator data was incomplete.");
        }

        byte[] rpIdHash = java.util.Arrays.copyOfRange(authenticatorData, 0, 32);
        int flags = authenticatorData[32] & 0xFF;
        int signCount = ByteBuffer.wrap(authenticatorData, 33, 4).order(ByteOrder.BIG_ENDIAN).getInt();

        String aaguid = null;
        byte[] attestedCredentialId = new byte[0];
        if ((flags & 0x40) != 0) {
            if (authenticatorData.length < 55) {
                throw new PasskeyValidationException("Authenticator attested credential data was incomplete.");
            }
            byte[] aaguidBytes = java.util.Arrays.copyOfRange(authenticatorData, 37, 53);
            aaguid = formatAaguid(aaguidBytes);
            int credentialIdLength = ByteBuffer.wrap(authenticatorData, 53, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
            int credentialIdStart = 55;
            int credentialIdEnd = credentialIdStart + credentialIdLength;
            if (authenticatorData.length < credentialIdEnd) {
                throw new PasskeyValidationException("Authenticator credential ID was incomplete.");
            }
            attestedCredentialId = java.util.Arrays.copyOfRange(authenticatorData, credentialIdStart, credentialIdEnd);
        }

        return new AuthenticatorData(
                rpIdHash,
                (flags & 0x01) != 0,
                (flags & 0x04) != 0,
                (flags & 0x40) != 0,
                signCount,
                aaguid,
                attestedCredentialId
        );
    }

    private String formatAaguid(byte[] bytes) {
        String hex = java.util.HexFormat.of().formatHex(bytes);
        return String.join("-",
                hex.substring(0, 8),
                hex.substring(8, 12),
                hex.substring(12, 16),
                hex.substring(16, 20),
                hex.substring(20, 32));
    }

    private byte[] decodeBase64Url(String value, String fieldName) {
        if (isBlank(value)) {
            throw new PasskeyValidationException(fieldName + " is required.");
        }
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new PasskeyValidationException(fieldName + " was not valid base64url data.");
        }
    }

    private String encodeUuid(UUID userId) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(userId.getMostSignificantBits());
        buffer.putLong(userId.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new PasskeyValidationException(key + " is missing from the passkey response.");
    }

    private String asText(Object value) {
        return value instanceof String text ? text : null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private String buildOrigin(URI uri) {
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
        return uri.getScheme() + "://" + uri.getHost() + (defaultPort ? "" : ":" + port);
    }

    private String displayNameFor(ArkilUser user) {
        return firstNonBlank(user.getDisplayName(), user.getUsername(), user.getEmail());
    }

    private String defaultLabel(String label) {
        return isBlank(label) ? "Passkey " + LABEL_DATE.format(LocalDate.now()) : label.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record RegistrationOptions(PasskeyFlowState flow, Map<String, Object> options) {}

    public record AuthenticationOptions(PasskeyFlowState flow, Map<String, Object> options) {}

    public record AuthenticationResult(ArkilUser user, PasskeyCredential credential) {}

    private record AuthenticatorData(
            byte[] rpIdHash,
            boolean userPresent,
            boolean userVerified,
            boolean attestedCredentialDataIncluded,
            int signCount,
            String aaguid,
            byte[] attestedCredentialId
    ) {}
}
