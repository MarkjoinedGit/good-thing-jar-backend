package com.goodthingjar.controller;

import com.goodthingjar.dto.ApiResponse;
import com.goodthingjar.dto.request.LoginRequest;
import com.goodthingjar.dto.request.RegisterRequest;
import com.goodthingjar.dto.response.AuthResponse;
import com.goodthingjar.dto.response.TokenRefreshResponse;
import com.goodthingjar.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register user", description = "Create account and issue access and refresh tokens")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already exists")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(success(response, servletRequest));
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticate with email/password and issue tokens")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthResponse response = userService.authenticate(request);
        return ResponseEntity.ok(success(response, servletRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Issue a new access token from a refresh token")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid refresh token")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
        @RequestHeader(name = "Authorization", required = false) String authorization,
        HttpServletRequest servletRequest
    ) {
        String token = extractBearerToken(authorization);
        TokenRefreshResponse response = userService.refreshAccessToken(token);
        return ResponseEntity.ok(success(response, servletRequest));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Logout current session (stateless acknowledgement)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(HttpServletRequest servletRequest) {
        return ResponseEntity.ok(success(Map.of("message", "Logged out"), servletRequest));
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return new ApiResponse<>(true, data, null, Instant.now(), resolveRequestId(request));
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }
}
