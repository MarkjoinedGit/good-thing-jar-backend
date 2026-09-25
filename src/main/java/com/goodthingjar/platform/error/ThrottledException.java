package com.goodthingjar.platform.error;

public final class ThrottledException extends ApiException {
  private final long retryAfterSeconds;

  public ThrottledException(long retryAfterSeconds) {
    super(ProblemCode.THROTTLED, "The operation is temporarily throttled");
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
