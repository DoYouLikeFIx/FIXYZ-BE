package com.fix.channel.vo;

public class AuthLoginResult {

  private final Long memberId;
  private final String sessionMode;

  private AuthLoginResult(Long memberId, String sessionMode) {
    this.memberId = memberId;
    this.sessionMode = sessionMode;
  }

  public static AuthLoginResult of(Long memberId, String sessionMode) {
    return new AuthLoginResult(memberId, sessionMode);
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getSessionMode() {
    return sessionMode;
  }
}
