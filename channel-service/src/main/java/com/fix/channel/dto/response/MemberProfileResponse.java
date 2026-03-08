package com.fix.channel.dto.response;

import com.fix.channel.vo.MemberProfileResult;
import java.time.Instant;

public class MemberProfileResponse {

  private final Long memberId;
  private final String email;
  private final String name;
  private final String role;
  private final Instant createdAt;

  private MemberProfileResponse(Long memberId, String email, String name, String role, Instant createdAt) {
    this.memberId = memberId;
    this.email = email;
    this.name = name;
    this.role = role;
    this.createdAt = createdAt;
  }

  public static MemberProfileResponse from(MemberProfileResult result) {
    return new MemberProfileResponse(
        result.getMemberId(),
        result.getEmail(),
        result.getName(),
        result.getRole(),
        result.getCreatedAt()
    );
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
