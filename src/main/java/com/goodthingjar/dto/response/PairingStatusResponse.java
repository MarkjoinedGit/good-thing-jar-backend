package com.goodthingjar.dto.response;

import com.goodthingjar.entity.enums.CoupleStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PairingStatusResponse(
    boolean isPaired,
    UUID coupleId,
    UUID partnerId,
    String partnerName,
    String partnerEmail,
    String partnerProfilePicture,
    OffsetDateTime pairedAt,
    CoupleStatus status,
    JarSummaryResponse currentJar,
    String message
) {
}

