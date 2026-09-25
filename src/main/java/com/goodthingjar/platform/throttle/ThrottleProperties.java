package com.goodthingjar.platform.throttle;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gtj.throttle")
public class ThrottleProperties {
  private Limit authentication = new Limit(10, Duration.ofMinutes(1));
  private Limit verificationResend = new Limit(3, Duration.ofMinutes(5));
  private Limit invitationCreate = new Limit(10, Duration.ofMinutes(1));
  private Limit invitationRetry = new Limit(5, Duration.ofMinutes(1));
  private Limit entryWrite = new Limit(30, Duration.ofMinutes(1));

  public static class Limit {
    private int capacity;
    private Duration window;

    public Limit() {}

    public Limit(int c, Duration w) {
      capacity = c;
      window = w;
    }

    public int getCapacity() {
      return capacity;
    }

    public void setCapacity(int v) {
      capacity = v;
    }

    public Duration getWindow() {
      return window;
    }

    public void setWindow(Duration v) {
      window = v;
    }
  }

  public Limit getAuthentication() {
    return authentication;
  }

  public void setAuthentication(Limit v) {
    authentication = v;
  }

  public Limit getVerificationResend() {
    return verificationResend;
  }

  public void setVerificationResend(Limit v) {
    verificationResend = v;
  }

  public Limit getInvitationCreate() {
    return invitationCreate;
  }

  public void setInvitationCreate(Limit v) {
    invitationCreate = v;
  }

  public Limit getInvitationRetry() {
    return invitationRetry;
  }

  public void setInvitationRetry(Limit v) {
    invitationRetry = v;
  }

  public Limit getEntryWrite() {
    return entryWrite;
  }

  public void setEntryWrite(Limit v) {
    entryWrite = v;
  }

  public Limit forOperation(String operation) {
    return switch (operation) {
      case "authentication" -> authentication;
      case "verification-resend" -> verificationResend;
      case "invitation-create" -> invitationCreate;
      case "invitation-retry" -> invitationRetry;
      case "entry-write" -> entryWrite;
      default -> throw new IllegalArgumentException("Unknown throttle operation");
    };
  }
}
