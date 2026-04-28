package com.goodthingjar.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EntryCreatedEvent(
    UUID entryId,
    UUID jarId,
    UUID authorId,
    OffsetDateTime createdAt
) {
}

