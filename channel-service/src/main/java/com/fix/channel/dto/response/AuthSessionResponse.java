package com.fix.channel.dto.response;

import com.fix.channel.vo.AuthSessionResult;

public class AuthSessionResponse {

  private final String memberUuid;
  private final String username;
  private final String email;
  private final String name;
  private final String role;
  private final boolean totpEnrolled;
  private final String accountId;

  private AuthSessionResponse(
      String memberUuid,
      String username,
      String email,
      String name,
      String role,
      boolean totpEnrolled,
      String accountId
  ) {
    this.memberUuid = memberUuid;
    this.username = username;
    this.email = email;
    this.name = name;
    this.role = role;
    this.totpEnrolled = totpEnrolled;
    this.accountId = accountId;
  }

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

  public String getMemberUuid() {
    return memberUuid;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getRole() {
    return role;
  }

  public boolean isTotpEnrolled() {
    return totpEnrolled;
  }

  public String getAccountId() {
    return accountId;
  }
}
