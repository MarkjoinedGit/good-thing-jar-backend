package com.goodthingjar.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserProfileResponse(
    UUID userId,
    String email,
    String firstName,
    String lastName,
    String profilePictureUrl,
    OffsetDateTime createdAt,
    OffsetDateTime lastLogin
) {
}

