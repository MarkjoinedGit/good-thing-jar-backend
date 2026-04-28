package com.goodthingjar.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserRegisteredEvent(
    UUID userId,
    String email,
    OffsetDateTime occurredAt
) {
}

