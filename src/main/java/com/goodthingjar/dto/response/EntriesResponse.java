package com.goodthingjar.dto.response;

import com.goodthingjar.entity.enums.JarStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EntriesResponse(
    UUID jarId,
    JarStatus status,
    OffsetDateTime unlocksAt,
    boolean unlocked,
    int totalEntries,
    List<?> entries
) {
}

