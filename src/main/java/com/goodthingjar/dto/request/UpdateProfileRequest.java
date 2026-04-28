package com.goodthingjar.dto.request;

public record UpdateProfileRequest(
    String firstName,
    String lastName,
    String profilePictureUrl
) {
}

