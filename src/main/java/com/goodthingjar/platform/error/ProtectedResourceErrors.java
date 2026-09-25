package com.goodthingjar.platform.error;

public final class ProtectedResourceErrors {
  private ProtectedResourceErrors() {}

  public static ApiException notFound() {
    return new ApiException(ProblemCode.PROTECTED_NOT_FOUND, "Resource not found");
  }
}
