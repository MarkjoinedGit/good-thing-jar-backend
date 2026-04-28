package com.goodthingjar.unit.security;

import com.goodthingjar.entity.User;
import com.goodthingjar.repository.UserRepository;
import com.goodthingjar.security.JwtAuthenticationFilter;
import com.goodthingjar.security.JwtTokenProvider;
import com.goodthingjar.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSetAuthenticationWhenTokenIsValidAndUserExists() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T00:00:00Z"));
        JwtTokenProvider provider = new JwtTokenProvider(resolveJwtSecret(), 3600, 86400, "good-thing-jar", clock);
        UserRepository userRepository = mock(UserRepository.class);

        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "user@example.com");

        User user = User.builder().id(userId).email("user@example.com").passwordHash("hash").build();
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserPrincipal userPrincipal = assertInstanceOf(UserPrincipal.class, principal);
        assertEquals(userId, userPrincipal.userId());
        assertEquals("user@example.com", userPrincipal.email());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void shouldLeaveSecurityContextEmptyWhenAuthorizationHeaderMissing() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-20T00:00:00Z"));
        JwtTokenProvider provider = new JwtTokenProvider(resolveJwtSecret(), 3600, 86400, "good-thing-jar", clock);
        UserRepository userRepository = mock(UserRepository.class);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(any(), any());
    }

    private static final class MutableClock extends Clock {

        private final Instant current;

        private MutableClock(Instant current) {
            this.current = current;
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
            return current;
        }
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
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
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
}



