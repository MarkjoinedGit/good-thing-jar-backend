package com.goodthingjar.dto.response;

import com.goodthingjar.entity.enums.EntryStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateEntryResponse(
    UUID entryId,
    UUID jarId,
    EntryStatus status,
    OffsetDateTime createdAt,
    String message
) {
}

