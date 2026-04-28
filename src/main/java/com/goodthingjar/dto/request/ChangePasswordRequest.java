package com.goodthingjar.dto.request;

public record ChangePasswordRequest(
    String oldPassword,
    String newPassword
) {
}

