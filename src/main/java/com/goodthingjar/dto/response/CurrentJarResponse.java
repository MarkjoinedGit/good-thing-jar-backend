package com.goodthingjar.dto.response;

import com.goodthingjar.entity.enums.JarStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CurrentJarResponse(
    UUID jarId,
    JarStatus status,
    OffsetDateTime unlocksAt,
    int entryCount,
    OffsetDateTime createdAt,
    boolean canWrite,
    UUID partnerId,
    String partnerName
) {
}

