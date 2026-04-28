package com.goodthingjar.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LockedEntryResponse(
    UUID entryId,
    String status,
    OffsetDateTime createdAt,
    String message
) {
}

