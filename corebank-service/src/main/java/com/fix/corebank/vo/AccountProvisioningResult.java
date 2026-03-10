package com.fix.corebank.vo;

import java.time.Instant;

public class AccountProvisioningResult {

  private final Long accountId;
  private final String accountNumber;
  private final String status;
  private final boolean idempotent;
  private final Long memberId;
  private final Instant createdAt;

  private AccountProvisioningResult(
      Long accountId,
      String accountNumber,
      String status,
      boolean idempotent,
      Long memberId,
      Instant createdAt
  ) {
    this.accountId = accountId;
    this.accountNumber = accountNumber;
    this.status = status;
    this.idempotent = idempotent;
    this.memberId = memberId;
    this.createdAt = createdAt;
  }

  public static AccountProvisioningResult of(
      Long accountId,
      String accountNumber,
      String status,
      boolean idempotent,
      Long memberId,
      Instant createdAt
  ) {
    return new AccountProvisioningResult(accountId, accountNumber, status, idempotent, memberId, createdAt);
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public String getStatus() {
    return status;
  }

  public boolean isIdempotent() {
    return idempotent;
  }

  public Long getMemberId() {
    return memberId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
