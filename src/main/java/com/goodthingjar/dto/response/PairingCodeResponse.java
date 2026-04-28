package com.goodthingjar.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PairingCodeResponse(
    UUID pairingCodeId,
    String code,
    OffsetDateTime expiresAt,
    String instructions
) {
}

