package com.goodthingjar.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PairingBusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public PairingBusinessException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static PairingBusinessException userAlreadyPaired() {
        return new PairingBusinessException(
            HttpStatus.CONFLICT,
            "USER_ALREADY_PAIRED",
                "You are already paired with someone or has pending pairing code. Cannot generate new code."
        );
    }

    public static PairingBusinessException activePairingCodeExists() {
        return new PairingBusinessException(
                HttpStatus.CONFLICT,
                "ACTIVE_PAIRING_CODE_EXISTS",
                "You already had active pairing code. Use the current code or wait until it expired"
        );
    }

    public static PairingBusinessException invalidCodeFormat() {
        return new PairingBusinessException(
            HttpStatus.BAD_REQUEST,
            "INVALID_CODE_FORMAT",
            "Code must be 12 alphanumeric characters (e.g., ABCD-1234-EFGH)"
        );
    }

    public static PairingBusinessException cannotPairWithSelf() {
        return new PairingBusinessException(
                HttpStatus.BAD_REQUEST,
                "CANNOT_PAIR_WITH_SELF",
                "Cannot pair with yourself"
        );
    }

    public static PairingBusinessException codeNotFound() {
        return new PairingBusinessException(
            HttpStatus.NOT_FOUND,
            "CODE_NOT_FOUND",
            "Pairing code not found"
        );
    }

    public static PairingBusinessException codeExpiredOrClaimed() {
        return new PairingBusinessException(
            HttpStatus.GONE,
            "CODE_EXPIRED_OR_CLAIMED",
            "This code has expired or already been claimed"
        );
    }

    public static PairingBusinessException alreadyPaired() {
        return new PairingBusinessException(
            HttpStatus.CONFLICT,
            "ALREADY_PAIRED",
            "You are already paired with someone else"
        );
    }
}
