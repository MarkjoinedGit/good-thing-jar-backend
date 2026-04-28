package com.goodthingjar.unit.security;

import com.goodthingjar.security.JwtTokenProvider;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    @Test
    void shouldGenerateValidAccessTokenWithExpectedClaims() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T00:00:00Z"));
        JwtTokenProvider provider = new JwtTokenProvider(resolveJwtSecret(), 3600, 7200, "good-thing-jar", clock);
        UUID userId = UUID.randomUUID();

        String token = provider.generateAccessToken(userId, "user@example.com");

        assertTrue(provider.validateToken(token));
        assertTrue(provider.isAccessToken(token));
        assertFalse(provider.isRefreshToken(token));
        assertEquals(userId, provider.extractUserId(token));
        assertEquals("user@example.com", provider.extractEmail(token));
        assertEquals(userId.toString(), provider.extractSubject(token));
    }

    @Test
    void shouldGenerateValidRefreshToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T00:00:00Z"));
        JwtTokenProvider provider = new JwtTokenProvider(resolveJwtSecret(), 3600, 86400, "good-thing-jar", clock);

        String token = provider.generateRefreshToken(UUID.randomUUID(), "refresh@example.com");

        assertTrue(provider.validateToken(token));
        assertTrue(provider.isRefreshToken(token));
        assertFalse(provider.isAccessToken(token));
    }

    @Test
    void shouldMarkTokenAsExpiredAfterExpiryTime() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T00:00:00Z"));
        JwtTokenProvider provider = new JwtTokenProvider(resolveJwtSecret(), 1, 10, "good-thing-jar", clock);

        String token = provider.generateAccessToken(UUID.randomUUID(), "expired@example.com");
        clock.advanceSeconds(2);

        assertFalse(provider.validateToken(token));
        assertTrue(provider.isTokenExpired(token));
    }

    @Test
    void shouldRejectBlankToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T00:00:00Z"));
        JwtTokenProvider provider = new JwtTokenProvider(resolveJwtSecret(), 3600, 86400, "good-thing-jar", clock);

        assertFalse(provider.validateToken(" "));
        assertThrows(IllegalArgumentException.class, () -> provider.extractSubject(" "));
    }

    @Test
    void shouldRejectWeakSecret() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T00:00:00Z"));

        assertThrows(WeakKeyException.class, () -> new JwtTokenProvider("weak-secret", 3600, 86400, "good-thing-jar", clock));
    }

    private static String resolveJwtSecret() {
        String fromEnv = System.getenv("JWT_SECRET");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            throw new IllegalStateException("JWT_SECRET is missing. Set it in environment or .env file.");
        }

        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                String[] parts = trimmed.split("=", 2);
                if ("JWT_SECRET".equals(parts[0].trim())) {
                    String value = parts[1].trim();
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read .env for JWT_SECRET", ex);
        }

        throw new IllegalStateException("JWT_SECRET is missing. Set it in environment or .env file.");
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            this.instant = this.instant.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}




