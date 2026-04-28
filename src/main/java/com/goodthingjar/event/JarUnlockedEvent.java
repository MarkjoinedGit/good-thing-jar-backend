package com.goodthingjar.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JarUnlockedEvent(
    UUID jarId,
    UUID coupleId,
    OffsetDateTime unlockedAt
) {
}

