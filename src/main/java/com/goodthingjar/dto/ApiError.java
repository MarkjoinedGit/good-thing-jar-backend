package com.goodthingjar.dto;

public record ApiError(
    String code,
    String message,
    Object details
) {
}

