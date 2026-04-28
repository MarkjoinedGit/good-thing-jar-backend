package com.goodthingjar.dto;

import java.time.Instant;

public record ApiResponse<T>(
    boolean success,
    T data,
    ApiError error,
    Instant timestamp,
    String requestId
) {
}

