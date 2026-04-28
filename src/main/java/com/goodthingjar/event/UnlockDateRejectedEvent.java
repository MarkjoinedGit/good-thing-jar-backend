package com.goodthingjar.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UnlockDateRejectedEvent(
    UUID proposalId,
    UUID jarId,
    UUID rejectedBy,
    String reason,
    OffsetDateTime occurredAt
) {
}

