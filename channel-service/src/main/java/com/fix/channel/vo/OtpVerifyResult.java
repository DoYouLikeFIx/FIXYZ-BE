package com.fix.channel.vo;

import java.time.Instant;

public class OtpVerifyResult {

  private final boolean verified;
  private final String memberUuid;
  private final String email;
  private final String name;
  private final String role;
  private final boolean totpEnrolled;
  private final String accountId;
  private final String accountNumber;
  private final Instant mfaVerifiedAt;

  private OtpVerifyResult(
      boolean verified,
      String memberUuid,
      String email,
      String name,
      String role,
      boolean totpEnrolled,
      String accountId,
      String accountNumber,
      Instant mfaVerifiedAt
  ) {
    this.verified = verified;
    this.memberUuid = memberUuid;
    this.email = email;
    this.name = name;
    this.role = role;
    this.totpEnrolled = totpEnrolled;
    this.accountId = accountId;
    this.accountNumber = accountNumber;
    this.mfaVerifiedAt = mfaVerifiedAt;
  }

  public static OtpVerifyResult verified(
      String memberUuid,
      String email,
      String name,
      String role,
      boolean totpEnrolled,
      String accountId,
      String accountNumber,
      Instant mfaVerifiedAt
  ) {
    return new OtpVerifyResult(
        true,
        memberUuid,
        email,
        name,
        role,
        totpEnrolled,
        accountId,
        accountNumber,
        mfaVerifiedAt
    );
  }

  public boolean isVerified() {
    return verified;
  }

  public String getMemberUuid() {
    return memberUuid;
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

  public Instant getMfaVerifiedAt() {
    return mfaVerifiedAt;
  }
}
