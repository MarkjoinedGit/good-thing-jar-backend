package com.goodthingjar.service.factory;

import com.goodthingjar.entity.Couple;
import com.goodthingjar.entity.Jar;
import com.goodthingjar.entity.enums.JarStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class JarFactory {
    public Jar createByCouple(Couple couple) {
        OffsetDateTime defaultUnlocksAt = OffsetDateTime.now()
                .withMonth(12)
                .withDayOfMonth(31)
                .withHour(23)
                .withMinute(59)
                .withSecond(59)
                .withNano(0);

        return Jar.builder()
                .couple(couple)
                .status(JarStatus.WRITABLE)
                .unlocksAt(defaultUnlocksAt)
                .entryCount(0)
                .build();
    }
}
