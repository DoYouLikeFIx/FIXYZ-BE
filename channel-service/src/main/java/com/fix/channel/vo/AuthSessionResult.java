package com.fix.channel.vo;

public class AuthSessionResult {

  private final String memberUuid;
  private final String username;
  private final String email;
  private final String name;
  private final String role;
  private final boolean totpEnrolled;
  private final String accountId;
  private final String accountNumber;

  private AuthSessionResult(
      String memberUuid,
      String username,
      String email,
      String name,
      String role,
      boolean totpEnrolled,
      String accountId,
      String accountNumber
  ) {
    this.memberUuid = memberUuid;
    this.username = username;
    this.email = email;
    this.name = name;
    this.role = role;
    this.totpEnrolled = totpEnrolled;
    this.accountId = accountId;
    this.accountNumber = accountNumber;
  }

  public static AuthSessionResult of(
      String memberUuid,
      String username,
      String email,
      String name,
      String role,
      boolean totpEnrolled,
      String accountId,
      String accountNumber
  ) {
    return new AuthSessionResult(memberUuid, username, email, name, role, totpEnrolled, accountId, accountNumber);
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

  public String getAccountNumber() {
    return accountNumber;
  }
}
