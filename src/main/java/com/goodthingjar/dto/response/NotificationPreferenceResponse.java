package com.goodthingjar.dto.response;

import com.goodthingjar.entity.enums.Frequency;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationPreferenceResponse(
    UUID notificationPreferenceId,
    UUID userId,
    boolean enabled,
    Frequency frequency,
    String localTimezone,
    LocalTime notificationTime,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

