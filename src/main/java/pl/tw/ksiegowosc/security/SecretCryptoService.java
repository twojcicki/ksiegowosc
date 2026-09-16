package pl.tw.ksiegowosc.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import pl.tw.ksiegowosc.config.EncryptionProperties;

@Service
public class SecretCryptoService {

    public static final String PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private static volatile SecretCryptoService instance;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCryptoService(EncryptionProperties properties) {
        this.secretKey = decodeKey(properties == null ? null : properties.key());
        instance = this;
    }

    static SecretCryptoService requireInstance() {
        SecretCryptoService current = instance;
        if (current == null) {
            throw new IllegalStateException("SecretCryptoService is not initialized");
        }
        return current;
    }

    /** Visible for tests — clears the static holder between unit tests. */
    static void clearInstanceForTests() {
        instance = null;
    }

    public boolean looksEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt secret", ex);
        }
    }

    public String decrypt(String stored) {
        if (!StringUtils.hasText(stored)) {
            return stored;
        }
        if (!looksEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (combined.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("Encrypted secret payload is truncated");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt secret — check APP_ENCRYPTION_KEY", ex);
        }
    }

    private static SecretKey decodeKey(String base64Key) {
        if (!StringUtils.hasText(base64Key)) {
            throw new IllegalStateException(
                    "Missing app.encryption.key / APP_ENCRYPTION_KEY (Base64-encoded 32-byte AES key). "
                            + "Generate with: openssl rand -base64 32");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid app.encryption.key / APP_ENCRYPTION_KEY: must be Base64", ex);
        }
        if (raw.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "Invalid app.encryption.key / APP_ENCRYPTION_KEY: decoded length must be 32 bytes, was "
                            + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }
}
