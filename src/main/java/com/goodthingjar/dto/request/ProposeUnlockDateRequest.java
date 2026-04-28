package com.goodthingjar.dto.request;

import java.time.OffsetDateTime;

public record ProposeUnlockDateRequest(
    OffsetDateTime newUnlocksAt
) {
}

