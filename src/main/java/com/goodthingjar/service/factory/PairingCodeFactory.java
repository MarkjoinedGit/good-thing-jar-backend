package com.goodthingjar.service.factory;

import com.goodthingjar.entity.PairingCode;
import com.goodthingjar.entity.User;
import com.goodthingjar.entity.enums.PairingCodeStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class PairingCodeFactory {
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int BLOCK_LENGTH = 4;
    private static final int BLOCK_COUNT = 3;

    public PairingCode create(String code, User user) {
        OffsetDateTime now = OffsetDateTime.now();

        return PairingCode.builder()
                .code(code)
                .generatedBy(user)
                .expiresAt(now.plusDays(1))
                .status(PairingCodeStatus.ACTIVE)
                .build();
    }

    public String generateCode() {
        return IntStream.range(0, BLOCK_COUNT)
                .mapToObj(i -> randomBlock())
                .collect(Collectors.joining("-"));
    }

    private static String randomBlock() {
        StringBuilder sb = new StringBuilder(BLOCK_LENGTH);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
