package com.goodthingjar.dto.response;

import com.goodthingjar.entity.Couple;
import com.goodthingjar.entity.User;
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
    public static PairingStatusResponse notPaired() {
        return new PairingStatusResponse(
                false, null, null, null, null, null, null, null, null,
                "You are not currently paired"
        );
    }

    public static PairingStatusResponse paired(Couple couple, User partner, JarSummaryResponse jarSummary) {
        return new PairingStatusResponse(
                true,
                couple.getId(),
                partner.getId(),
                partner.getFullName(),
                partner.getEmail(),
                partner.getProfilePictureUrl(),
                couple.getCreatedAt(),
                couple.getStatus(),
                jarSummary,
                null
        );
    }
}

