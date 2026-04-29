package com.goodthingjar.unit.service;

import com.goodthingjar.dto.request.LoginRequest;
import com.goodthingjar.dto.request.RegisterRequest;
import com.goodthingjar.dto.response.AuthResponse;
import com.goodthingjar.entity.User;
import com.goodthingjar.repository.UserRepository;
import com.goodthingjar.security.JwtTokenProvider;
import com.goodthingjar.service.UserService;
import com.goodthingjar.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    @Test
    void registerShouldCreateUserAndReturnTokens() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);

        UserService userService = new UserServiceImpl(userRepository, passwordEncoder, tokenProvider);
        ReflectionTestUtils.setField(
                userService,
                "accessTokenExpirySeconds",
                86400
        );
        RegisterRequest request = new RegisterRequest("new@example.com", "Strong123!", "New", "User");

        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Strong123!")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
            return user;
        });
        when(tokenProvider.generateAccessToken(UUID.fromString("11111111-1111-1111-1111-111111111111"), "new@example.com"))
            .thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(UUID.fromString("11111111-1111-1111-1111-111111111111"), "new@example.com"))
            .thenReturn("refresh-token");

        AuthResponse response = userService.register(request);

        assertEquals("new@example.com", response.email());
        assertEquals("access-token", response.token());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(86400, response.expiresIn());
    }

    @Test
    void authenticateShouldReturnTokensForValidCredentials() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);

        UserService userService = new UserServiceImpl(userRepository, passwordEncoder, tokenProvider);
        ReflectionTestUtils.setField(
                userService,
                "accessTokenExpirySeconds",
                86400
        );

        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        User user = User.builder()
            .id(userId)
            .email("user@example.com")
            .passwordHash("encoded")
            .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Strong123!", "encoded")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenProvider.generateAccessToken(userId, "user@example.com")).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(userId, "user@example.com")).thenReturn("refresh-token");

        AuthResponse response = userService.authenticate(new LoginRequest("user@example.com", "Strong123!"));

        assertNotNull(response.userId());
        assertEquals("access-token", response.token());
        assertEquals("refresh-token", response.refreshToken());
    }

    @Test
    void authenticateShouldThrowUnauthorizedForInvalidCredentials() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);

        UserService userService = new UserServiceImpl(userRepository, passwordEncoder, tokenProvider);
        ReflectionTestUtils.setField(
                userService,
                "accessTokenExpirySeconds",
                86400
        );

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.authenticate(new LoginRequest("user@example.com", "bad")));
    }
}
