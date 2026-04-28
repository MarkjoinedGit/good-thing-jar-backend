package com.goodthingjar.dto.request;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OfflineEntryRequest(
    UUID clientGeneratedId,
    String content,
    OffsetDateTime createdAt
) {
}

