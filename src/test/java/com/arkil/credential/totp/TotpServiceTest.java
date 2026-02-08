package com.arkil.credential.totp;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TOTP generation and verification.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TotpServiceTest {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    @Test
    @Order(1)
    @DisplayName("Base32 encoding produces valid output")
    void base32EncodingValid() {
        byte[] input = "Hello".getBytes();
        String encoded = encodeBase32(input);

        // Should only contain valid Base32 characters
        for (char c : encoded.toCharArray()) {
            assertTrue(BASE32_ALPHABET.indexOf(c) >= 0,
                    "Invalid character in Base32 output: " + c);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Base32 encode/decode roundtrip")
    void base32Roundtrip() {
        byte[] original = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 };
        String encoded = encodeBase32(original);
        byte[] decoded = decodeBase32(encoded);

        assertArrayEquals(original, decoded);
    }

    @Test
    @Order(3)
    @DisplayName("TOTP code generation produces correct length")
    void totpCodeLength() {
        byte[] secret = decodeBase32("JBSWY3DPEHPK3PXP"); // Test secret
        long timeStep = System.currentTimeMillis() / 1000 / 30;

        String code = generateCode(secret, timeStep, "SHA1", 6);

        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"), "Code should be 6 digits");
    }

    @Test
    @Order(4)
    @DisplayName("Same time step produces same code")
    void sameTimeStepSameCode() {
        byte[] secret = decodeBase32("JBSWY3DPEHPK3PXP");
        long timeStep = 1000000L;

        String code1 = generateCode(secret, timeStep, "SHA1", 6);
        String code2 = generateCode(secret, timeStep, "SHA1", 6);

        assertEquals(code1, code2);
    }

    @Test
    @Order(5)
    @DisplayName("Different time steps produce different codes")
    void differentTimeStepsDifferentCodes() {
        byte[] secret = decodeBase32("JBSWY3DPEHPK3PXP");

        String code1 = generateCode(secret, 1000000L, "SHA1", 6);
        String code2 = generateCode(secret, 1000001L, "SHA1", 6);

        assertNotEquals(code1, code2);
    }

    // Helper methods (copied from TotpService for unit testing)

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
        java.util.List<Byte> bytes = new java.util.ArrayList<>();
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

    private String generateCode(byte[] secret, long timeStep, String algorithm, int digits) {
        try {
            byte[] timeBytes = java.nio.ByteBuffer.allocate(8).putLong(timeStep).array();

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("Hmac" + algorithm);
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "RAW"));
            byte[] hash = mac.doFinal(timeBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) |
                    ((hash[offset + 1] & 0xFF) << 16) |
                    ((hash[offset + 2] & 0xFF) << 8) |
                    (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", otp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
