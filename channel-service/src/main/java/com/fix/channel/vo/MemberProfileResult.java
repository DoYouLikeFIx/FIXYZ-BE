package com.fix.channel.vo;

import java.time.Instant;

public class MemberProfileResult {

  private final Long memberId;
  private final String email;
  private final String name;
  private final String role;
  private final Instant createdAt;

  private MemberProfileResult(Long memberId, String email, String name, String role, Instant createdAt) {
    this.memberId = memberId;
    this.email = email;
    this.name = name;
    this.role = role;
    this.createdAt = createdAt;
  }

  public static MemberProfileResult of(Long memberId, String email, String name, String role, Instant createdAt) {
    return new MemberProfileResult(memberId, email, name, role, createdAt);
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

  public String getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
