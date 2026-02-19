package com.arkil.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for sensitive fields (TOTP secrets, OAuth client secrets).
 *
 * Format: Base64( IV(12) || ciphertext || authTag(16) )
 *
 * The encryption key is derived from the property arkil.security.encryption-key.
 * In production, set this to a 32-byte (256-bit) Base64-encoded key.
 * Generate with: openssl rand -base64 32
 */
@Service
@Slf4j
public class SecretEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    @Value("${arkil.security.encryption-key:}")
    private String encryptionKeyBase64;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void init() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            log.warn("arkil.security.encryption-key not set — using derived dev key. Set a proper key for production!");
            // Derive a deterministic dev key (NOT for production)
            byte[] devKey = new byte[32];
            byte[] seed = "arkil-dev-encryption-key-do-not-use-in-prod".getBytes();
            System.arraycopy(seed, 0, devKey, 0, Math.min(seed.length, 32));
            secretKey = new SecretKeySpec(devKey, "AES");
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            if (keyBytes.length != 32) {
                throw new IllegalStateException("Encryption key must be exactly 32 bytes (256 bits). Got " + keyBytes.length);
            }
            secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info("Secret encryption initialized with configured key");
        }
    }

    /**
     * Encrypt a plaintext string. Returns Base64-encoded ciphertext.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // Prepend IV to ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt a Base64-encoded ciphertext. Returns plaintext string.
     */
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            // If decryption fails, the value might be stored in plaintext (pre-encryption migration)
            log.debug("Decryption failed — returning raw value (may be plaintext from before encryption was enabled)");
            return encrypted;
        }
    }
}
