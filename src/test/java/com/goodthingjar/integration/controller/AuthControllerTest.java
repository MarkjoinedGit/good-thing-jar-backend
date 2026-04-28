package com.goodthingjar.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodthingjar.controller.AuthController;
import com.goodthingjar.controller.UserController;
import com.goodthingjar.dto.request.LoginRequest;
import com.goodthingjar.dto.request.RegisterRequest;
import com.goodthingjar.dto.response.AuthResponse;
import com.goodthingjar.dto.response.UserProfileResponse;
import com.goodthingjar.security.UserPrincipal;
import com.goodthingjar.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(userService), new UserController(userService))
            .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerShouldReturnCreated() throws Exception {
        AuthResponse response = new AuthResponse(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "user@example.com",
            "John",
            "Doe",
            "access",
            "refresh",
            86400
        );

        when(userService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("user@example.com", "Strong123!", "John", "Doe"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    @Test
    void loginShouldReturnOk() throws Exception {
        AuthResponse response = new AuthResponse(
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            "user@example.com",
            "John",
            "Doe",
            "access",
            "refresh",
            86400
        );

        when(userService.authenticate(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("user@example.com", "Strong123!"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").value("access"));
    }

    @Test
    void getCurrentUserShouldReturnProfile() throws Exception {
        UUID userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId, "user@example.com"),
                null,
                AuthorityUtils.NO_AUTHORITIES
            )
        );

        UserProfileResponse profile = new UserProfileResponse(
            userId,
            "user@example.com",
            "John",
            "Doe",
            null,
            OffsetDateTime.parse("2026-04-20T00:00:00Z"),
            OffsetDateTime.parse("2026-04-20T10:00:00Z")
        );
        when(userService.getCurrentUserProfile(userId)).thenReturn(profile);

        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("user@example.com"));
    }

    @Test
    void deleteAccountShouldReturnOk() throws Exception {
        UUID userId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId, "user@example.com"),
                null,
                AuthorityUtils.NO_AUTHORITIES
            )
        );

        doNothing().when(userService).deleteAccount(userId);

        mockMvc.perform(delete("/api/v1/users/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.message").value("Account deleted"));
    }
}
