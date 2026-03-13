package com.fix.channel.dto.response;

import java.time.Instant;

import com.fix.channel.vo.AuthLoginResult;

public record AuthLoginResponse(
    String loginToken,
    String nextAction,
    boolean totpEnrolled,
    Instant expiresAt
) {

  public static AuthLoginResponse from(AuthLoginResult result) {
    return new AuthLoginResponse(
        result.getLoginToken(),
        result.getNextAction(),
        result.isTotpEnrolled(),
        result.getExpiresAt()
    );
  }
}
