package com.goodthingjar.notification.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class DeliverySecretCipher {
  private final byte[] key;
  private final SecureRandom random = new SecureRandom();

  public DeliverySecretCipher(
      @Value("${gtj.security.verification-delivery-key:development-only-change-me}")
          String configured) {
    if (configured == null || configured.length() < 32) {
      throw new IllegalStateException(
          "gtj.security.verification-delivery-key must contain at least 32 characters");
    }
    try {
      key =
          MessageDigest.getInstance("SHA-256").digest(configured.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      byte[] ciphertext = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
    } catch (Exception e) {
      throw new IllegalStateException("Could not protect delivery secret", e);
    }
  }

  public String decrypt(String encoded) {
    try {
      byte[] all = Base64.getDecoder().decode(encoded);
      ByteBuffer b = ByteBuffer.wrap(all);
      byte[] iv = new byte[12];
      b.get(iv);
      byte[] ciphertext = new byte[b.remaining()];
      b.get(ciphertext);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
      return new String(c.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Could not open delivery secret", e);
    }
  }
}
