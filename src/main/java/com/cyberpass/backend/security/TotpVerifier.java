package com.cyberpass.backend.security;

import jakarta.enterprise.context.ApplicationScoped;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Self-contained RFC 6238 TOTP verification utility.
 * Handles secure secret generation, Base32 decoding, dynamic HMAC-SHA1 truncation,
 * clock drift tolerance (±30s), and scratch backup codes generation.
 */
@ApplicationScoped
public class TotpVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    
    // Characters used in Base32 encoding (RFC 4648)
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    @ConfigProperty(name = "passproof.mfa.issuer", defaultValue = "PassProof")
    String mfaIssuer;

    /**
     * Generates a secure, random 160-bit (20 bytes) secret key encoded in Base32.
     */
    public String generateSecret() {
        SecureRandom random = new SecureRandom();
        byte[] buffer = new byte[20];
        random.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    /**
     * Generates the otpauth URI standard for authenticator apps (Google/Microsoft Authenticator).
     */
    public String generateOtpAuthUri(String email, String secret) {
        try {
            String encodedIssuer = URLEncoder.encode(mfaIssuer, StandardCharsets.UTF_8.name()).replace("+", "%20");
            String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8.name()).replace("+", "%20");
            return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                encodedIssuer, encodedEmail, secret, encodedIssuer
            );
        } catch (Exception e) {
            return String.format(
                "otpauth://totp/PassProof:%s?secret=%s&issuer=PassProof&algorithm=SHA1&digits=6&period=30",
                email, secret
            );
        }
    }

    /**
     * Verifies the 6-digit code. Supports clock drift by checking current, previous, and next intervals.
     */
    public boolean verifyCode(String base32Secret, int code) {
        byte[] secretBytes;
        try {
            secretBytes = decodeBase32(base32Secret);
        } catch (IllegalArgumentException e) {
            return false;
        }

        long currentInterval = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;

        // Check window: previous, current, and next time steps to handle network latency or clock drift
        for (int i = -1; i <= 1; i++) {
            long timeInterval = currentInterval + i;
            if (calculateTotp(secretBytes, timeInterval) == code) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates an array of 8-character numeric/alphanumeric backup scratch codes.
     */
    public List<String> generateBackupCodes() {
        SecureRandom random = new SecureRandom();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            // Generate a random 8-digit code
            int codeVal = 10000000 + random.nextInt(90000000);
            codes.add(String.valueOf(codeVal));
        }
        return codes;
    }

    private int calculateTotp(byte[] secret, long timeInterval) {
        byte[] data = new byte[8];
        long value = timeInterval;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }

        try {
            SecretKeySpec signKey = new SecretKeySpec(secret, HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signKey);
            byte[] hmacResult = mac.doFinal(data);

            // Dynamic Truncation (RFC 4226 / RFC 6238)
            int offset = hmacResult[hmacResult.length - 1] & 0x0F;
            int truncatedHash = 0;
            for (int i = 0; i < 4; ++i) {
                truncatedHash <<= 8;
                truncatedHash |= (hmacResult[offset + i] & 0xFF);
            }
            truncatedHash &= 0x7FFFFFFF;
            return truncatedHash % (int) Math.pow(10, CODE_DIGITS);
        } catch (Exception e) {
            throw new RuntimeException("Error executing TOTP calculation", e);
        }
    }

    // --- Base32 Encoding / Decoding Helper Methods ---

    private String encodeBase32(byte[] bytes) {
        StringBuilder sb = new StringBuilder((bytes.length * 8 + 4) / 5);
        int val = 0;
        int bits = 0;
        for (byte b : bytes) {
            val = (val << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                sb.append(BASE32_CHARS.charAt((val >> bits) & 0x1F));
            }
        }
        if (bits > 0) {
            sb.append(BASE32_CHARS.charAt((val << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    private byte[] decodeBase32(String base32) {
        String cleaned = base32.toUpperCase().replace("-", "").replace(" ", "");
        int len = cleaned.length();
        int outLen = (len * 5) / 8;
        byte[] bytes = new byte[outLen];
        int val = 0;
        int bits = 0;
        int outIdx = 0;

        for (int i = 0; i < len; i++) {
            char c = cleaned.charAt(i);
            int charVal = BASE32_CHARS.indexOf(c);
            if (charVal == -1) {
                if (c == '=') break; // Ignore padding
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            val = (val << 5) | charVal;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                if (outIdx < outLen) {
                    bytes[outIdx++] = (byte) ((val >> bits) & 0xFF);
                }
            }
        }
        return bytes;
    }
}
