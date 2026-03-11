package com.fix.channel.dto.response;

import com.fix.channel.vo.AuthRegisterResult;
import java.time.Instant;

public record AuthRegisterResponse(Long memberId, String email, String name, Instant createdAt) {

  public static AuthRegisterResponse from(AuthRegisterResult result) {
    return new AuthRegisterResponse(
        result.getMemberId(),
        result.getEmail(),
        result.getName(),
        result.getCreatedAt()
    );
  }
}
