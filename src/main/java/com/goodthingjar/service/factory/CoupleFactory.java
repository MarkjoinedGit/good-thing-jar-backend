package com.goodthingjar.service.factory;

import com.goodthingjar.entity.Couple;
import com.goodthingjar.entity.PairingCode;
import com.goodthingjar.entity.enums.CoupleStatus;

import java.time.OffsetDateTime;

public class CoupleFactory {
    public Couple createByCode(PairingCode pc) {
        OffsetDateTime now = OffsetDateTime.now();

        return Couple.builder()
                .user1(pc.getGeneratedBy())
                .user2(pc.getClaimedBy())
                .pairedAt(now)
                .status(CoupleStatus.ACTIVE)
                .build();
    }
}
