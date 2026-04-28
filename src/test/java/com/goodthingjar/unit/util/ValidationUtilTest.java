package com.goodthingjar.unit.util;

import com.goodthingjar.util.ValidationUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationUtilTest {

    @Test
    void shouldAcceptPasswordWhenItHasMinimumLengthAndThreeCharacterTypes() {
        assertDoesNotThrow(() -> ValidationUtil.validatePassword("Strong123"));
        assertDoesNotThrow(() -> ValidationUtil.validatePassword("Strong!pass"));
    }

    @Test
    void shouldRejectPasswordWhenItDoesNotMeetStrengthRequirements() {
        assertThrows(IllegalArgumentException.class, () -> ValidationUtil.validatePassword("weak"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtil.validatePassword("alllowercase"));
    }

    @Test
    void shouldAcceptEmailWhenFormatIsValid() {
        assertDoesNotThrow(() -> ValidationUtil.validateEmail("user@example.com"));
        assertDoesNotThrow(() -> ValidationUtil.validateEmail("user.name+tag@example.co"));
    }

    @Test
    void shouldRejectEmailWhenFormatIsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> ValidationUtil.validateEmail("invalid-email"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtil.validateEmail("@example.com"));
    }
}

