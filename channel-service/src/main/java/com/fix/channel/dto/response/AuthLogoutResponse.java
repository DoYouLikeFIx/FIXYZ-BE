package com.fix.channel.dto.response;

public record AuthLogoutResponse(String message) {

  public static AuthLogoutResponse of(String message) {
    return new AuthLogoutResponse(message);
  }
}
