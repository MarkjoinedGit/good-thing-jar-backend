package com.goodthingjar.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UnpairResponse(
    UUID coupleId,
    OffsetDateTime unpairedAt,
    String message
) {
    public static UnpairResponse of(UUID coupleId) {
        return new UnpairResponse(
            coupleId,
            OffsetDateTime.now(),
            "Pairing has been ended. You retain permanent read-only access to already-unlocked entries"
        );
    }
}

