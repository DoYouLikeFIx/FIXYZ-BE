package com.fix.channel.dto.response;

public class AuthLogoutResponse {

  private final String message;

  private AuthLogoutResponse(String message) {
    this.message = message;
  }

  public static AuthLogoutResponse of(String message) {
    return new AuthLogoutResponse(message);
  }

  public String getMessage() {
    return message;
  }
}
