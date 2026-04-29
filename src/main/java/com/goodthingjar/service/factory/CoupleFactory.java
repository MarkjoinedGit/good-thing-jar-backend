package com.goodthingjar.service.factory;

import com.goodthingjar.entity.Couple;
import com.goodthingjar.entity.PairingCode;
import com.goodthingjar.entity.enums.CoupleStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashSet;

@Component
public class CoupleFactory {
    public Couple createByCode(PairingCode pc) {
        OffsetDateTime now = OffsetDateTime.now();

        Couple couple = Couple.builder()
                .user1(pc.getGeneratedBy())
                .user2(pc.getClaimedBy())
                .pairedAt(now)
                .jars(new HashSet<>())
                .status(CoupleStatus.ACTIVE)
                .build();
        return couple;
    }
}
