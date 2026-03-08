package com.fix.channel.vo;

public class AuthSessionResult {

  private final String memberUuid;
  private final String username;
  private final String email;
  private final String name;
  private final String role;
  private final boolean totpEnrolled;
  private final String accountId;

  private AuthSessionResult(
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

  public static AuthSessionResult of(
      String memberUuid,
      String username,
      String email,
      String name,
      String role,
      boolean totpEnrolled,
      String accountId
  ) {
    return new AuthSessionResult(memberUuid, username, email, name, role, totpEnrolled, accountId);
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
