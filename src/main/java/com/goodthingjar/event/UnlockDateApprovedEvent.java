package com.goodthingjar.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UnlockDateApprovedEvent(
    UUID proposalId,
    UUID jarId,
    UUID approvedBy,
    OffsetDateTime newUnlocksAt,
    OffsetDateTime occurredAt
) {
}

