package com.fix.channel.dto.response;

import com.fix.channel.vo.AuthRegisterResult;
import java.time.Instant;

public class AuthRegisterResponse {

  private final Long memberId;
  private final String email;
  private final String name;
  private final Instant createdAt;

  private AuthRegisterResponse(Long memberId, String email, String name, Instant createdAt) {
    this.memberId = memberId;
    this.email = email;
    this.name = name;
    this.createdAt = createdAt;
  }

  public static AuthRegisterResponse from(AuthRegisterResult result) {
    return new AuthRegisterResponse(
        result.getMemberId(),
        result.getEmail(),
        result.getName(),
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

  public Instant getCreatedAt() {
    return createdAt;
  }
}
