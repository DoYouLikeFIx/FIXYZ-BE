package com.fix.channel.vo;

public class AuthSessionResult {

  private final Long memberId;
  private final String email;
  private final String name;

  private AuthSessionResult(Long memberId, String email, String name) {
    this.memberId = memberId;
    this.email = email;
    this.name = name;
  }

  public static AuthSessionResult of(Long memberId, String email, String name) {
    return new AuthSessionResult(memberId, email, name);
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
