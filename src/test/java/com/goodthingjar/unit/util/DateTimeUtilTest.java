package com.goodthingjar.unit.util;

import com.goodthingjar.util.DateTimeUtil;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateTimeUtilTest {

    @Test
    void shouldConvertOffsetDateTimeToUtc() {
        OffsetDateTime input = OffsetDateTime.parse("2026-04-20T12:30:00+07:00");

        OffsetDateTime utc = DateTimeUtil.toUtc(input);

        assertEquals(ZoneOffset.UTC, utc.getOffset());
        assertEquals(Instant.parse("2026-04-20T05:30:00Z"), utc.toInstant());
    }

    @Test
    void shouldDetectUnlockReachedWhenTimeHasPassed() {
        Clock fixed = Clock.fixed(Instant.parse("2026-04-20T10:00:00Z"), ZoneOffset.UTC);

        assertTrue(DateTimeUtil.isUnlockReached(OffsetDateTime.parse("2026-04-20T09:59:59Z"), fixed));
        assertFalse(DateTimeUtil.isUnlockReached(OffsetDateTime.parse("2026-04-20T10:00:01Z"), fixed));
    }

    @Test
    void shouldConvertUtcInstantToProvidedZone() {
        ZonedDateTime converted = DateTimeUtil.convertUtcToZone(
            Instant.parse("2026-04-20T12:00:00Z"),
            "America/New_York"
        );

        assertEquals("America/New_York", converted.getZone().getId());
        assertEquals(8, converted.getHour());
    }

    @Test
    void shouldCreateDefaultUnlockDateAtEndOfDayUtc() {
        Clock fixed = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC);

        OffsetDateTime unlock = DateTimeUtil.defaultUnlockDateTime(12, 31, fixed);

        assertEquals(2026, unlock.getYear());
        assertEquals(12, unlock.getMonthValue());
        assertEquals(31, unlock.getDayOfMonth());
        assertEquals(23, unlock.getHour());
        assertEquals(59, unlock.getMinute());
        assertEquals(59, unlock.getSecond());
        assertEquals(ZoneOffset.UTC, unlock.getOffset());
    }

    @Test
    void shouldRollDefaultUnlockDateToNextYearWhenTodayIsTargetDate() {
        Clock fixed = Clock.fixed(Instant.parse("2026-12-31T00:00:00Z"), ZoneOffset.UTC);

        OffsetDateTime unlock = DateTimeUtil.defaultUnlockDateTime(12, 31, fixed);

        assertEquals(2027, unlock.getYear());
    }

    @Test
    void shouldRejectInvalidMonthDayCombination() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

        assertThrows(IllegalArgumentException.class, () -> DateTimeUtil.defaultUnlockDateTime(2, 30, fixed));
    }
}

