package com.fix.channel.dto.response;

import com.fix.channel.vo.MemberProfileResult;
import java.time.Instant;

public record MemberProfileResponse(
    Long memberId,
    String email,
    String name,
    String role,
    Instant createdAt
) {

  public static MemberProfileResponse from(MemberProfileResult result) {
    return new MemberProfileResponse(
        result.getMemberId(),
        result.getEmail(),
        result.getName(),
        result.getRole(),
        result.getCreatedAt()
    );
  }
}
