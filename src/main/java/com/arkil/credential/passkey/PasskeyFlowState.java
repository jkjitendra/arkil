package com.arkil.credential.passkey;

import java.time.Instant;
import java.util.UUID;

public record PasskeyFlowState(
        String flowId,
        PasskeyFlowType type,
        String challenge,
        UUID userId,
        String rpId,
        String origin,
        Instant expiresAt
) {
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
