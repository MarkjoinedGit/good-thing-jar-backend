package com.goodthingjar.controller;

import com.goodthingjar.dto.ApiResponse;
import com.goodthingjar.dto.request.ChangePasswordRequest;
import com.goodthingjar.dto.request.UpdateProfileRequest;
import com.goodthingjar.dto.response.UserProfileResponse;
import com.goodthingjar.security.SecurityUtils;
import com.goodthingjar.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get current user", description = "Return profile for the authenticated user")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile returned")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(HttpServletRequest request) {
        UserProfileResponse profile = userService.getCurrentUserProfile(SecurityUtils.currentUserId());
        return ResponseEntity.ok(success(profile, request));
    }

    @PatchMapping
    @Operation(summary = "Update current user", description = "Update profile fields for the authenticated user")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(@RequestBody UpdateProfileRequest body, HttpServletRequest request) {
        UserProfileResponse profile = userService.updateProfile(SecurityUtils.currentUserId(), body);
        return ResponseEntity.ok(success(profile, request));
    }

    @DeleteMapping
    @Operation(summary = "Delete account", description = "Soft-delete the authenticated user account")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deleted")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteAccount(HttpServletRequest request) {
        userService.deleteAccount(SecurityUtils.currentUserId());
        return ResponseEntity.ok(success(Map.of("message", "Account deleted"), request));
    }

    @PostMapping("/password")
    @Operation(summary = "Change password", description = "Validate old password and set a new password")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(@RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        userService.changePassword(SecurityUtils.currentUserId(), body);
        return ResponseEntity.ok(success(Map.of("message", "Password updated"), request));
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
