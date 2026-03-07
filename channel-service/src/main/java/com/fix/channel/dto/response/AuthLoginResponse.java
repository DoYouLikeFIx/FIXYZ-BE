package com.fix.channel.dto.response;

import com.fix.channel.vo.AuthLoginResult;

public class AuthLoginResponse {

  private final Long memberId;
  private final String sessionMode;

  private AuthLoginResponse(Long memberId, String sessionMode) {
    this.memberId = memberId;
    this.sessionMode = sessionMode;
  }

  public static AuthLoginResponse from(AuthLoginResult result) {
    return new AuthLoginResponse(result.getMemberId(), result.getSessionMode());
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getSessionMode() {
    return sessionMode;
  }
}
