package com.goodthingjar.dto.request;

import com.goodthingjar.entity.enums.Frequency;

import java.time.LocalTime;

public record UpdateNotificationPreferenceRequest(
    Boolean enabled,
    Frequency frequency,
    String localTimezone,
    LocalTime notificationTime
) {
}

