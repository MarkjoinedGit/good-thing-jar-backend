package com.goodthingjar.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public final class DateTimeUtil {

    private DateTimeUtil() {
    }

    public static OffsetDateTime nowUtc() {
        return OffsetDateTime.now(Clock.systemUTC());
    }

    public static OffsetDateTime toUtc(OffsetDateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("Date time must not be null");
        }
        return dateTime.withOffsetSameInstant(ZoneOffset.UTC);
    }

    public static ZonedDateTime convertUtcToZone(Instant utcInstant, String zoneId) {
        if (utcInstant == null) {
            throw new IllegalArgumentException("UTC instant must not be null");
        }
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("Timezone must not be blank");
        }
        return utcInstant.atZone(ZoneId.of(zoneId));
    }

    public static boolean isUnlockReached(OffsetDateTime unlocksAt) {
        return isUnlockReached(unlocksAt, Clock.systemUTC());
    }

    public static boolean isUnlockReached(OffsetDateTime unlocksAt, Clock clock) {
        if (unlocksAt == null) {
            throw new IllegalArgumentException("Unlock date must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("Clock must not be null");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        return !unlocksAt.isAfter(now);
    }

    public static OffsetDateTime defaultUnlockDateTime(int month, int day, Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock must not be null");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate candidate = buildDateForYear(today.getYear(), month, day);
        if (!candidate.isAfter(today)) {
            candidate = buildDateForYear(today.getYear() + 1, month, day);
        }

        return OffsetDateTime.of(candidate, LocalTime.of(23, 59, 59), ZoneOffset.UTC);
    }

    private static LocalDate buildDateForYear(int year, int month, int day) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        int maxDay = Year.of(year).atMonth(month).lengthOfMonth();
        if (day < 1 || day > maxDay) {
            throw new IllegalArgumentException("Day is invalid for the selected month/year");
        }
        return LocalDate.of(year, month, day);
    }
}

