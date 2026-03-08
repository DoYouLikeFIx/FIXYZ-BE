package com.fix.channel.dto.response;

import com.fix.channel.vo.AuthSessionResult;

public class AuthSessionResponse {

  private final Long memberId;
  private final String email;
  private final String name;

  private AuthSessionResponse(Long memberId, String email, String name) {
    this.memberId = memberId;
    this.email = email;
    this.name = name;
  }

  public static AuthSessionResponse from(AuthSessionResult result) {
    return new AuthSessionResponse(result.getMemberId(), result.getEmail(), result.getName());
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
