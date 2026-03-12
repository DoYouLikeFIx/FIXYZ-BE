package com.fix.channel.vo;

import java.time.Instant;

public class AuthLoginResult {

  private final String loginToken;
  private final String nextAction;
  private final boolean totpEnrolled;
  private final Instant expiresAt;

  private AuthLoginResult(String loginToken, String nextAction, boolean totpEnrolled, Instant expiresAt) {
    this.loginToken = loginToken;
    this.nextAction = nextAction;
    this.totpEnrolled = totpEnrolled;
    this.expiresAt = expiresAt;
  }

  public static AuthLoginResult of(String loginToken, String nextAction, boolean totpEnrolled, Instant expiresAt) {
    return new AuthLoginResult(loginToken, nextAction, totpEnrolled, expiresAt);
  }

  public String getLoginToken() {
    return loginToken;
  }

  public String getNextAction() {
    return nextAction;
  }

  public boolean isTotpEnrolled() {
    return totpEnrolled;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
