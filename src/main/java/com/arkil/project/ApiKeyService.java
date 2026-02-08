package com.arkil.project;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for API key management.
 * 
 * Key format (Stripe-style with embedded keyId for O(1) lookup):
 * - pk_live_<keyId>_<random> or pk_test_<keyId>_<random> (publishable, safe for client)
 * - sk_live_<keyId>_<random> or sk_test_<keyId>_<random> (secret, shown once, stored hashed)
 * 
 * The keyId is embedded in the key to allow O(1) database lookup during validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository keyRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${arkil.keys.rotation-grace-hours:24}")
    private int rotationGraceHours;

    private static final int RANDOM_BYTES = 16; // 22 chars base64
    private static final String PK_PREFIX = "pk_";
    private static final String SK_PREFIX = "sk_";

    /**
     * Create new API key pair for a project.
     * Returns the secret key plaintext (shown only once!).
     * 
     * Format: pk_live_<keyId>_<random>, sk_live_<keyId>_<random>
     */
    @Transactional
    public KeyPairResult createKeyPair(UUID projectId, String name, ApiKey.KeyType keyType) {
        String keyId = generateKeyId();
        String environment = keyType == ApiKey.KeyType.LIVE ? "live" : "test";

        // Generate publishable key: pk_live_<keyId>_<random>
        String pkRandom = generateRandomString();
        String publishableKey = PK_PREFIX + environment + "_" + keyId + "_" + pkRandom;

        // Generate secret key: sk_live_<keyId>_<random>
        String skRandom = generateRandomString();
        String secretKeyPlaintext = SK_PREFIX + environment + "_" + keyId + "_" + skRandom;
        String secretKeyHash = passwordEncoder.encode(secretKeyPlaintext);
        String last4 = skRandom.substring(skRandom.length() - 4);

        ApiKey apiKey = ApiKey.builder()
                .keyId(keyId)
                .name(name)
                .projectId(projectId)
                .publishableKey(publishableKey)
                .secretKeyHash(secretKeyHash)
                .secretKeyLast4(last4)
                .keyType(keyType)
                .status(ApiKey.KeyStatus.ACTIVE)
                .build();

        keyRepository.save(apiKey);

        log.info("Created {} API key {} for project {}", keyType, keyId, projectId);

        return new KeyPairResult(apiKey, secretKeyPlaintext);
    }

    /**
     * Rotate a key - create new key, put old in grace period.
     * Verifies key belongs to specified project.
     */
    @Transactional
    public KeyPairResult rotateKey(UUID projectId, UUID keyId, String rotatedBy) {
        ApiKey oldKey = keyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("Key not found: " + keyId));

        // Ownership check
        if (!oldKey.getProjectId().equals(projectId)) {
            throw new SecurityException("Key does not belong to project");
        }

        if (oldKey.getStatus() != ApiKey.KeyStatus.ACTIVE) {
            throw new IllegalStateException("Only active keys can be rotated");
        }

        // Put old key in rotating status with grace period
        oldKey.setStatus(ApiKey.KeyStatus.ROTATING);
        oldKey.setGracePeriodEndsAt(Instant.now().plus(rotationGraceHours, ChronoUnit.HOURS));
        keyRepository.save(oldKey);

        // Create new key
        KeyPairResult newKeyPair = createKeyPair(
                oldKey.getProjectId(),
                oldKey.getName() + " (rotated)",
                oldKey.getKeyType());

        log.info("Rotated key {} -> {}, grace period {} hours",
                oldKey.getKeyId(), newKeyPair.apiKey().getKeyId(), rotationGraceHours);

        return newKeyPair;
    }

    /**
     * Revoke a key immediately.
     * Verifies key belongs to specified project.
     */
    @Transactional
    public void revokeKey(UUID projectId, UUID keyId, String revokedBy, String reason) {
        ApiKey key = keyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("Key not found: " + keyId));

        // Ownership check
        if (!key.getProjectId().equals(projectId)) {
            throw new SecurityException("Key does not belong to project");
        }

        key.revoke(revokedBy, reason);
        keyRepository.save(key);

        log.warn("Key {} revoked by {}: {}", key.getKeyId(), revokedBy, reason);
    }

    /**
     * Validate a secret key against stored hash.
     * O(1) lookup by extracting embedded keyId from the secret.
     * 
     * Format: sk_live_<keyId>_<random>
     */
    @Transactional
    public Optional<ApiKey> validateSecretKey(String secretKeyPlaintext) {
        if (secretKeyPlaintext == null || !secretKeyPlaintext.startsWith(SK_PREFIX)) {
            return Optional.empty();
        }

        // Parse keyId from secret: sk_live_<keyId>_<random>
        String keyId = parseKeyIdFromSecret(secretKeyPlaintext);
        if (keyId == null) {
            log.debug("Invalid secret key format");
            return Optional.empty();
        }

        // Single query: lookup key + verify project not deleted (O(1), one DB hit)
        Optional<ApiKey> keyOpt = keyRepository.findByKeyIdWithActiveProject(keyId);
        if (keyOpt.isEmpty()) {
            log.debug("Key not found or project deleted: {}", keyId);
            return Optional.empty();
        }

        ApiKey key = keyOpt.get();
        Instant now = Instant.now();

        // Check if key is usable for auth (handles ACTIVE and ROTATING with grace period)
        if (!key.isUsableForAuth(now)) {
            log.debug("Key {} is not usable for auth (status: {}, gracePeriodEndsAt: {})", 
                    keyId, key.getStatus(), key.getGracePeriodEndsAt());
            return Optional.empty();
        }

        // Verify password hash
        if (!passwordEncoder.matches(secretKeyPlaintext, key.getSecretKeyHash())) {
            log.warn("Invalid secret key for keyId: {}", keyId);
            return Optional.empty();
        }

        // Mark as used (only updates if stale to reduce write amplification)
        if (key.markUsedIfStale()) {
            keyRepository.save(key);
        }

        return Optional.of(key);
    }

    /**
     * Find key by publishable key.
     * Enforces project not deleted and key usable for auth.
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> findByPublishableKey(String publishableKey) {
        Instant now = Instant.now();
        return keyRepository.findByPublishableKeyWithActiveProject(publishableKey)
                .filter(key -> key.isUsableForAuth(now));
    }

    /**
     * Get all keys for a project.
     */
    public List<ApiKey> getKeysForProject(UUID projectId) {
        return keyRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * Cleanup: Expire rotating keys past grace period.
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void expireRotatingKeys() {
        int expired = keyRepository.expireRotatingKeys(Instant.now());
        if (expired > 0) {
            log.info("Expired {} rotating keys past grace period", expired);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Parse keyId from secret key.
     * Format: sk_live_<keyId>_<random> or sk_test_<keyId>_<random>
     * keyId is now hex-only (no underscores): key<16hex>
     * 
     * @return keyId or null if format is invalid
     */
    private String parseKeyIdFromSecret(String secret) {
        // sk_live_keyabcdef12345678_xxxxx
        // Split: [sk, live, keyabcdef12345678, xxxxx]
        try {
            // Remove prefix (sk_)
            String withoutPrefix = secret.substring(SK_PREFIX.length());
            
            // Split by underscore: [live, keyId, random]
            String[] parts = withoutPrefix.split("_", 3);
            if (parts.length < 3) {
                return null;
            }
            
            // keyId is parts[1] (e.g., "keyabcdef12345678")
            String keyId = parts[1];
            
            // Validate it looks like a keyId (starts with "key", hex chars)
            if (!keyId.startsWith("key") || keyId.length() < 19) {
                return null;
            }
            
            return keyId;
        } catch (Exception e) {
            log.debug("Failed to parse keyId from secret: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Generate keyId using hex encoding (no underscores for safe parsing).
     * Format: key<16 hex chars> = 19 chars total
     */
    private String generateKeyId() {
        byte[] bytes = new byte[8];
        secureRandom.nextBytes(bytes);
        StringBuilder hex = new StringBuilder("key");
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString(); // e.g., "keyabcdef12345678"
    }

    private String generateRandomString() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Result of key creation containing both the entity and the plaintext secret.
     */
    public record KeyPairResult(ApiKey apiKey, String secretKeyPlaintext) {}
}

