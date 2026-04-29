package com.goodthingjar.util;

import com.goodthingjar.dto.request.ClaimPairingCodeRequest;
import com.goodthingjar.entity.PairingCode;
import com.goodthingjar.entity.User;
import com.goodthingjar.entity.enums.PairingCodeStatus;
import com.goodthingjar.exception.PairingBusinessException;
import com.goodthingjar.repository.CoupleRepository;
import com.goodthingjar.repository.PairingCodeRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

public final class ValidationUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        Pattern.CASE_INSENSITIVE
    );

    private ValidationUtil() {
    }

    public static void validatePassword(String password) {
        if (!isValidPassword(password)) {
            throw new IllegalArgumentException(
                "Password must be at least 8 characters and include at least 3 of 4 character types (uppercase, lowercase, number, symbol)"
            );
        }
    }

    public static void validateEmail(String email) {
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }

    public static void validateUnlockDate(OffsetDateTime unlockDate) {
        validateUnlockDate(unlockDate, Clock.systemUTC());
    }

    static void validateUnlockDate(OffsetDateTime unlockDate, Clock clock) {
        if (unlockDate == null) {
            throw new IllegalArgumentException("Unlock date must not be null");
        }

        OffsetDateTime nowUtc = OffsetDateTime.now(clock);
        if (!unlockDate.isAfter(nowUtc)) {
            throw new IllegalArgumentException("Unlock date must be in the future");
        }
    }

    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSymbol = true;
            }
        }

        int categoryCount = 0;
        if (hasUpper) {
            categoryCount++;
        }
        if (hasLower) {
            categoryCount++;
        }
        if (hasDigit) {
            categoryCount++;
        }
        if (hasSymbol) {
            categoryCount++;
        }

        return categoryCount >= 3;
    }

    public static boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static void validateUserCanGenerateCode(User user, CoupleRepository coupleRepository, PairingCodeRepository pairingCodeRepository) {

        if (coupleRepository.existsByUser1(user) || coupleRepository.existsByUser2(user)) {
            throw PairingBusinessException.userAlreadyPaired();
        }

        boolean hasActiveCode = pairingCodeRepository
                .existsByGeneratedByAndStatusAndExpiresAtAfter(
                        user,
                        PairingCodeStatus.ACTIVE,
                        OffsetDateTime.now()
                );

        if (hasActiveCode) {
            throw PairingBusinessException.activePairingCodeExists();
        }
    }

    public static void validateUserCanClaimCode(User user, CoupleRepository coupleRepository) {
        if (coupleRepository.existsByUser1(user) || coupleRepository.existsByUser2(user)) {
            throw PairingBusinessException.alreadyPaired();
        }
    }

    public static void validateClaimableCode(PairingCode pc, User claimer) {

        // A code may still carry ACTIVE status even after its 24-hour window
        // has passed if a background job hasn't flipped it yet. Check the
        // wall clock explicitly so callers always get CODE_EXPIRED_OR_CLAIMED.
        if (!pc.getStatus().equals(PairingCodeStatus.ACTIVE)
                || pc.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw PairingBusinessException.codeExpiredOrClaimed();
        }

        if (claimer.getId().equals(pc.getGeneratedBy().getId())) {
            throw PairingBusinessException.cannotPairWithSelf();
        }
    }

    public static boolean isValidFormat(String code) {
        if (code == null) return false;
        String regex = "^[A-Z0-9]{4}(-[A-Z0-9]{4}){2}$";
        return code.matches(regex);
    }
}

