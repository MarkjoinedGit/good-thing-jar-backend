package com.goodthingjar.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class SecretHasher {
  private final SecureRandom random = new SecureRandom();
  private final byte[] scopeKey;

  public SecretHasher(
      @Value("${gtj.security.throttle-scope-key:development-only-change-me}") String scopeKey) {
    if (scopeKey == null || scopeKey.length() < 32) {
      throw new IllegalStateException(
          "gtj.security.throttle-scope-key must contain at least 32 characters");
    }
    this.scopeKey = scopeKey.getBytes(StandardCharsets.UTF_8);
  }

  public String randomToken() {
    byte[] b = new byte[32];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public String hashToken(String token) {
    try {
      return hex(
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public String pseudonymousScope(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(scopeKey, "HmacSHA256"));
      return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String hex(byte[] bytes) {
    return java.util.HexFormat.of().formatHex(bytes);
  }
}
