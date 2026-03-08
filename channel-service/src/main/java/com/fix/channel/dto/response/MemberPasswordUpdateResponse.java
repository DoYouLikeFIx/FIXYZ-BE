package com.fix.channel.dto.response;

public class MemberPasswordUpdateResponse {

  private final String message;

  private MemberPasswordUpdateResponse(String message) {
    this.message = message;
  }

  public static MemberPasswordUpdateResponse of(String message) {
    return new MemberPasswordUpdateResponse(message);
  }

  public String getMessage() {
    return message;
  }
}
