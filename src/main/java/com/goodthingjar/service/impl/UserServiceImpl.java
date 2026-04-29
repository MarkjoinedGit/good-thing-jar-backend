package com.goodthingjar.service.impl;

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
import com.goodthingjar.service.UserService;
import com.goodthingjar.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.security.jwt.access-token-expiry-seconds}")
    private long accessTokenExpirySeconds;

    @Override
    public AuthResponse register(RegisterRequest request) {
        ValidationUtil.validateEmail(request.email());
        ValidationUtil.validatePassword(request.password());

        userRepository.findByEmail(request.email())
                .filter(user -> user.getDeletedAt() == null)
                .ifPresent(user -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
                });

        User user = User.builder()
                .email(request.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .build();

        User saved = userRepository.save(user);
        String token = jwtTokenProvider.generateAccessToken(saved.getId(), saved.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(saved.getId(), saved.getEmail());

        return DtoMapper.toAuthResponse(saved, token, refreshToken, accessTokenExpirySeconds);
    }

    @Override
    public AuthResponse authenticate(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        }

        user.setLastLogin(OffsetDateTime.now(ZoneOffset.UTC));
        User updated = userRepository.save(user);

        String token = jwtTokenProvider.generateAccessToken(updated.getId(), updated.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(updated.getId(), updated.getEmail());
        return DtoMapper.toAuthResponse(updated, token, refreshToken, accessTokenExpirySeconds);
    }

    @Override
    public TokenRefreshResponse refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || !jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        UUID userId = jwtTokenProvider.extractUserId(refreshToken);
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not active"));

        String token = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        return new TokenRefreshResponse(token, accessTokenExpirySeconds);
    }

    @Override
    public UserProfileResponse getCurrentUserProfile(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return DtoMapper.toUserProfileResponse(user);
    }

    @Override
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.profilePictureUrl() != null) {
            user.setProfilePictureUrl(request.profilePictureUrl());
        }

        User saved = userRepository.save(user);
        return DtoMapper.toUserProfileResponse(saved);
    }

    @Override
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }

        ValidationUtil.validatePassword(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Override
    public void deleteAccount(UUID userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        user.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userRepository.save(user);
    }
}
