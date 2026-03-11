package com.fix.corebank.vo;

import java.time.Instant;

public class AccountStatusResult {

  private final Long accountId;
  private final Long memberId;
  private final String accountNumber;
  private final String status;
  private final boolean orderEligible;
  private final String denialCode;
  private final Instant asOf;

  private AccountStatusResult(
      Long accountId,
      Long memberId,
      String accountNumber,
      String status,
      boolean orderEligible,
      String denialCode,
      Instant asOf
  ) {
    this.accountId = accountId;
    this.memberId = memberId;
    this.accountNumber = accountNumber;
    this.status = status;
    this.orderEligible = orderEligible;
    this.denialCode = denialCode;
    this.asOf = asOf;
  }

  public static AccountStatusResult of(
      Long accountId,
      Long memberId,
      String accountNumber,
      String status,
      boolean orderEligible,
      String denialCode,
      Instant asOf
  ) {
    return new AccountStatusResult(accountId, memberId, accountNumber, status, orderEligible, denialCode, asOf);
  }

  public Long getAccountId() {
    return accountId;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public String getStatus() {
    return status;
  }

  public boolean isOrderEligible() {
    return orderEligible;
  }

  public String getDenialCode() {
    return denialCode;
  }

  public Instant getAsOf() {
    return asOf;
  }
}
