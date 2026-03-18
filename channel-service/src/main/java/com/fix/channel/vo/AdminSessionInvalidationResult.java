package com.fix.channel.vo;

public class AdminSessionInvalidationResult {

  private final String memberUuid;
  private final int invalidatedCount;
  private final String message;

  private AdminSessionInvalidationResult(String memberUuid, int invalidatedCount, String message) {
    this.memberUuid = memberUuid;
    this.invalidatedCount = invalidatedCount;
    this.message = message;
  }

  public static AdminSessionInvalidationResult of(String memberUuid, int invalidatedCount, String message) {
    return new AdminSessionInvalidationResult(memberUuid, invalidatedCount, message);
  }

  public String getMemberUuid() {
    return memberUuid;
  }

  public int getInvalidatedCount() {
    return invalidatedCount;
  }

  public String getMessage() {
    return message;
  }
}
