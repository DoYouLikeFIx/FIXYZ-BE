package com.fix.channel.dto.response;

import com.fix.channel.vo.AuthLoginResult;

public record AuthLoginResponse(Long memberId, String email, String name) {

  public static AuthLoginResponse from(AuthLoginResult result) {
    return new AuthLoginResponse(result.getMemberId(), result.getEmail(), result.getName());
  }
}
