package com.goodthingjar.unit.util;

import com.goodthingjar.util.EncryptionUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptionUtilTest {

    @Test
    void shouldEncryptAndDecryptContent() {
        EncryptionUtil util = new EncryptionUtil("AES", resolveEncryptionKey(), new SecureRandom());

        String encrypted = util.encryptContent("This is a private jar entry");
        String decrypted = util.decryptContent(encrypted);

        assertNotEquals("This is a private jar entry", encrypted);
        assertEquals("This is a private jar entry", decrypted);
    }

    @Test
    void shouldProduceDifferentCipherTextForSameInput() {
        EncryptionUtil util = new EncryptionUtil("AES", resolveEncryptionKey(), new SecureRandom());

        String encrypted1 = util.encryptContent("same-content");
        String encrypted2 = util.encryptContent("same-content");

        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void shouldThrowOnInvalidCipherPayload() {
        EncryptionUtil util = new EncryptionUtil("AES", resolveEncryptionKey(), new SecureRandom());

        assertThrows(IllegalArgumentException.class, () -> util.decryptContent("not-base64"));
    }

    @Test
    void shouldRejectBlankPlainText() {
        EncryptionUtil util = new EncryptionUtil("AES", resolveEncryptionKey(), new SecureRandom());

        assertThrows(IllegalArgumentException.class, () -> util.encryptContent(" "));
    }

    private static String resolveEncryptionKey() {
        String fromEnv = System.getenv("ENCRYPTION_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            throw new IllegalStateException("ENCRYPTION_KEY is missing. Set it in environment or .env file.");
        }

        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                String[] parts = trimmed.split("=", 2);
                if ("ENCRYPTION_KEY".equals(parts[0].trim())) {
                    String value = parts[1].trim();
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read .env for ENCRYPTION_KEY", ex);
        }

        throw new IllegalStateException("ENCRYPTION_KEY is missing. Set it in environment or .env file.");
    }
}

