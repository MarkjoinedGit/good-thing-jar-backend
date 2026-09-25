package com.goodthingjar.platform.error;

public class ApiException extends RuntimeException {
  private final ProblemCode problemCode;

  public ApiException(ProblemCode problemCode, String safeMessage) {
    super(safeMessage);
    this.problemCode = problemCode;
  }

  public ProblemCode problemCode() {
    return problemCode;
  }
}
