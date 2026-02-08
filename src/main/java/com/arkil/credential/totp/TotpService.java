package com.arkil.credential.totp;

import com.arkil.audit.ActorType;
import com.arkil.audit.AuditEventType;
import com.arkil.audit.AuditService;
import com.arkil.user.ArkilUser;
import com.arkil.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Service for TOTP enrollment and verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_LENGTH = 20; // 160 bits
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_LENGTH = 8;

    private final TotpCredentialRepository totpCredentialRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Generate a new TOTP secret for enrollment.
     * Returns the secret in Base32 format for QR code generation.
     */
    @Transactional
    public TotpEnrollmentResponse startEnrollment(UUID userId, String issuer) {
        ArkilUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Generate random secret
        byte[] secretBytes = new byte[SECRET_LENGTH];
        new SecureRandom().nextBytes(secretBytes);
        String secretBase32 = encodeBase32(secretBytes);

        // Create or update TOTP credential (not yet enabled)
        TotpCredential credential = totpCredentialRepository.findByUserId(userId)
                .orElseGet(() -> TotpCredential.builder()
                        .user(user)
                        .build());

        credential.setSecretEncrypted(secretBase32); // TODO: Encrypt in production
        credential.setEnabled(false);
        totpCredentialRepository.save(credential);

        // Generate provisioning URI for QR code
        String provisioningUri = String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=%s&digits=%d&period=%d",
                issuer, user.getEmail(),
                secretBase32, issuer,
                credential.getAlgorithm(), credential.getDigits(), credential.getPeriod()
        );

        return new TotpEnrollmentResponse(secretBase32, provisioningUri);
    }

    /**
     * Verify and confirm TOTP enrollment.
     */
    @Transactional
    public boolean confirmEnrollment(UUID userId, String code) {
        TotpCredential credential = totpCredentialRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("TOTP not enrolled"));

        if (credential.getEnabled()) {
            throw new IllegalStateException("TOTP already enabled");
        }

        boolean valid = verifyCode(credential.getSecretEncrypted(), code,
                credential.getAlgorithm(), credential.getDigits(), credential.getPeriod());

        if (valid) {
            // Generate backup codes
            List<String> backupCodes = generateBackupCodes();
            credential.setBackupCodesHash(hashBackupCodes(backupCodes));
            credential.setEnabled(true);
            credential.setConfirmedAt(Instant.now());
            totpCredentialRepository.save(credential);

            auditService.logSuccess(AuditEventType.MFA_ENROLLED, userId.toString(),
                    ActorType.USER, "totp", getCurrentRequest());

            log.info("TOTP enrollment confirmed for user: {}", userId);
        }

        return valid;
    }

    /**
     * Verify a TOTP code for authentication.
     */
    @Transactional
    public boolean verify(UUID userId, String code) {
        TotpCredential credential = totpCredentialRepository.findByUserId(userId)
                .orElse(null);

        if (credential == null || !credential.getEnabled()) {
            return false;
        }

        boolean valid = verifyCode(credential.getSecretEncrypted(), code,
                credential.getAlgorithm(), credential.getDigits(), credential.getPeriod());

        if (valid) {
            credential.setLastUsedAt(Instant.now());
            totpCredentialRepository.save(credential);

            auditService.logSuccess(AuditEventType.MFA_VERIFIED, userId.toString(),
                    ActorType.USER, "totp", getCurrentRequest());
        } else {
            auditService.logFailure(AuditEventType.MFA_FAILED, userId.toString(),
                    ActorType.USER, "totp", "Invalid code", getCurrentRequest());
        }

        return valid;
    }

    /**
     * Check if user has TOTP enabled.
     */
    public boolean isEnabled(UUID userId) {
        return totpCredentialRepository.findByUserId(userId)
                .map(TotpCredential::getEnabled)
                .orElse(false);
    }

    private boolean verifyCode(String secretBase32, String code, String algorithm, int digits, int period) {
        try {
            byte[] secret = decodeBase32(secretBase32);
            long timeStep = Instant.now().getEpochSecond() / period;

            // Check current and adjacent time windows (clock drift tolerance)
            for (int i = -1; i <= 1; i++) {
                String expected = generateCode(secret, timeStep + i, algorithm, digits);
                if (expected.equals(code)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("TOTP verification failed", e);
            return false;
        }
    }

    private String generateCode(byte[] secret, long timeStep, String algorithm, int digits)
            throws GeneralSecurityException {

        byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();

        Mac mac = Mac.getInstance("Hmac" + algorithm);
        mac.init(new SecretKeySpec(secret, "RAW"));
        byte[] hash = mac.doFinal(timeBytes);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24) |
                ((hash[offset + 1] & 0xFF) << 16) |
                ((hash[offset + 2] & 0xFF) << 8) |
                (hash[offset + 3] & 0xFF);

        int otp = binary % (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", otp);
    }

    private String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsInBuffer = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                int index = (buffer >> (bitsInBuffer - 5)) & 0x1F;
                result.append(BASE32_ALPHABET.charAt(index));
                bitsInBuffer -= 5;
            }
        }

        if (bitsInBuffer > 0) {
            int index = (buffer << (5 - bitsInBuffer)) & 0x1F;
            result.append(BASE32_ALPHABET.charAt(index));
        }

        return result.toString();
    }

    private byte[] decodeBase32(String encoded) {
        List<Byte> bytes = new ArrayList<>();
        int buffer = 0;
        int bitsInBuffer = 0;

        for (char c : encoded.toUpperCase().toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
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

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                code.append(random.nextInt(10));
            }
            codes.add(code.toString());
        }

        return codes;
    }

    private String hashBackupCodes(List<String> codes) {
        // In production, hash each code individually with bcrypt
        return String.join(",", codes); // Simplified for demo
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public record TotpEnrollmentResponse(String secret, String provisioningUri) {}
}
