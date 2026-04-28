package com.goodthingjar.dto.response;

import com.goodthingjar.entity.enums.EntryStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UnlockedEntryResponse(
    UUID entryId,
    UUID authorId,
    String authorName,
    String content,
    OffsetDateTime createdAt,
    EntryStatus status
) {
}

