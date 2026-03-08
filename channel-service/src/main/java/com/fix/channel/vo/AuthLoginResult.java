package com.fix.channel.vo;

public class AuthLoginResult {

  private final Long memberId;
  private final String email;
  private final String name;

  private AuthLoginResult(Long memberId, String email, String name) {
    this.memberId = memberId;
    this.email = email;
    this.name = name;
  }

  public static AuthLoginResult of(Long memberId, String email, String name) {
    return new AuthLoginResult(memberId, email, name);
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
