package com.goodthingjar.platform.error;

import org.springframework.http.HttpStatus;

public enum ProblemCode {
  VALIDATION(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed"),
  AUTHENTICATION(HttpStatus.UNAUTHORIZED, "authentication_required", "Authentication required"),
  EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "email_not_verified", "Email verification required"),
  PROTECTED_NOT_FOUND(HttpStatus.NOT_FOUND, "resource_not_found", "Resource not found"),
  CONFLICT(HttpStatus.CONFLICT, "state_conflict", "Request conflicts with current state"),
  LOCKED(HttpStatus.LOCKED, "jar_locked", "Jar is locked"),
  THROTTLED(HttpStatus.TOO_MANY_REQUESTS, "temporarily_throttled", "Try again later");

  private final HttpStatus status;
  private final String code;
  private final String title;

  ProblemCode(HttpStatus status, String code, String title) {
    this.status = status;
    this.code = code;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String title() {
    return title;
  }
}
