package com.goodthingjar.dto.request;

public record LoginRequest(
    String email,
    String password
) {
}

