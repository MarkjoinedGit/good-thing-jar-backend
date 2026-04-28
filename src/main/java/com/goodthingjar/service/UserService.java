package com.goodthingjar.service;

import com.goodthingjar.dto.mapper.DtoMapper;
import com.goodthingjar.dto.request.ChangePasswordRequest;
import com.goodthingjar.dto.request.LoginRequest;
import com.goodthingjar.dto.request.RegisterRequest;
import com.goodthingjar.dto.request.UpdateProfileRequest;
import com.goodthingjar.dto.response.AuthResponse;
import com.goodthingjar.dto.response.TokenRefreshResponse;
import com.goodthingjar.dto.response.UserProfileResponse;
import com.goodthingjar.entity.User;
import com.goodthingjar.repository.UserRepository;
import com.goodthingjar.security.JwtTokenProvider;
import com.goodthingjar.util.ValidationUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public interface UserService {
    AuthResponse register(RegisterRequest request);
    AuthResponse authenticate(LoginRequest request);
    TokenRefreshResponse refreshAccessToken(String refreshToken);
    UserProfileResponse getCurrentUserProfile(UUID userId);
    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);
    void changePassword(UUID userId, ChangePasswordRequest request);
    void deleteAccount(UUID userId);
}

