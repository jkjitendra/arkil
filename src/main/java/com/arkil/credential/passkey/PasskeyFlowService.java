package com.arkil.credential.passkey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PasskeyFlowService {

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private final ConcurrentMap<String, PasskeyFlowState> flows = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public PasskeyFlowService(@Value("${arkil.webauthn.timeout-ms:60000}") long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public PasskeyFlowState create(PasskeyFlowType type, UUID userId, String rpId, String origin) {
        cleanupExpired();

        String flowId = UUID.randomUUID().toString();
        byte[] challengeBytes = new byte[32];
        SECURE_RANDOM.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);

        PasskeyFlowState state = new PasskeyFlowState(
                flowId,
                type,
                challenge,
                userId,
                rpId,
                origin,
                Instant.now().plusMillis(timeoutMillis)
        );
        flows.put(flowId, state);
        return state;
    }

    public PasskeyFlowState consume(String flowId, PasskeyFlowType expectedType) {
        cleanupExpired();
        PasskeyFlowState state = flows.remove(flowId);
        if (state == null || state.isExpired() || state.type() != expectedType) {
            throw new PasskeyValidationException("Passkey request expired. Start again.");
        }
        return state;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        flows.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
