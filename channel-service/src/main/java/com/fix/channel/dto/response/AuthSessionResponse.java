package com.fix.channel.dto.response;

import com.fix.channel.vo.AuthSessionResult;

public record AuthSessionResponse(
    String memberUuid,
    String username,
    String email,
    String name,
    String role,
    boolean totpEnrolled,
    String accountId
) {

  public static AuthSessionResponse from(AuthSessionResult result) {
    return new AuthSessionResponse(
        result.getMemberUuid(),
        result.getUsername(),
        result.getEmail(),
        result.getName(),
        result.getRole(),
        result.isTotpEnrolled(),
        result.getAccountId()
    );
  }
}
