package com.fix.channel.dto.response;

import com.fix.channel.vo.AuthLoginResult;

public class AuthLoginResponse {

  private final Long memberId;
  private final String email;
  private final String name;

  private AuthLoginResponse(Long memberId, String email, String name) {
    this.memberId = memberId;
    this.email = email;
    this.name = name;
  }

  public static AuthLoginResponse from(AuthLoginResult result) {
    return new AuthLoginResponse(result.getMemberId(), result.getEmail(), result.getName());
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }
}
