package com.goodthingjar.dto.response;

import java.util.UUID;

public record AuthResponse(
    UUID userId,
    String email,
    String firstName,
    String lastName,
    String token,
    String refreshToken,
    long expiresIn
) {
}

