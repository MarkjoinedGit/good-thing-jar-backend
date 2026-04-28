package com.goodthingjar.dto.response;

public record TokenRefreshResponse(
    String token,
    long expiresIn
) {
}

