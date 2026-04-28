package com.goodthingjar.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final String algorithm;
    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;

    @Autowired
    public EncryptionUtil(
        @Value("${app.encryption.algorithm}") String algorithm,
        @Value("${app.encryption.key}") String key
    ) {
        this(algorithm, key, new SecureRandom());
    }

    public EncryptionUtil(String algorithm, String key, SecureRandom secureRandom) {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("Encryption algorithm must not be blank");
        }
        if (!"AES".equalsIgnoreCase(algorithm)) {
            throw new IllegalArgumentException("Unsupported encryption algorithm: " + algorithm);
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Encryption key must not be blank");
        }

        this.algorithm = algorithm;
        this.secretKey = new SecretKeySpec(deriveKeyBytes(key), this.algorithm);
        this.secureRandom = secureRandom;
    }

    public String encryptContent(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("Content to encrypt must not be blank");
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt content", ex);
        }
    }

    public String decryptContent(String encodedCipherText) {
        if (encodedCipherText == null || encodedCipherText.isBlank()) {
            throw new IllegalArgumentException("Content to decrypt must not be blank");
        }

        try {
            byte[] encryptedPayload = Base64.getDecoder().decode(encodedCipherText);
            if (encryptedPayload.length <= GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Encrypted payload is invalid");
            }

            ByteBuffer buffer = ByteBuffer.wrap(encryptedPayload);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("Encrypted payload cannot be decrypted", ex);
        }
    }

    private byte[] deriveKeyBytes(String key) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to derive AES-256 key", ex);
        }
    }
}
