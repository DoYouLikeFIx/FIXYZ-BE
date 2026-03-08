package com.fix.channel.vo;

import java.time.Instant;

public class AuthRegisterResult {

  private final Long memberId;
  private final String email;
  private final String name;
  private final Instant createdAt;

  private AuthRegisterResult(Long memberId, String email, String name, Instant createdAt) {
    this.memberId = memberId;
    this.email = email;
    this.name = name;
    this.createdAt = createdAt;
  }

  public static AuthRegisterResult of(Long memberId, String email, String name, Instant createdAt) {
    return new AuthRegisterResult(memberId, email, name, createdAt);
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

  public Instant getCreatedAt() {
    return createdAt;
  }
}
