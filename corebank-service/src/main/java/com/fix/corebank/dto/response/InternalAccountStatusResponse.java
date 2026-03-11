package com.fix.corebank.dto.response;

import com.fix.corebank.vo.AccountStatusResult;
import java.time.Instant;

public class InternalAccountStatusResponse {

  private final Long accountId;
  private final Long memberId;
  private final String accountNumber;
  private final String status;
  private final boolean orderEligible;
  private final String denialCode;
  private final Instant asOf;

  private InternalAccountStatusResponse(
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

  public static InternalAccountStatusResponse from(AccountStatusResult result) {
    return new InternalAccountStatusResponse(
        result.getAccountId(),
        result.getMemberId(),
        result.getAccountNumber(),
        result.getStatus(),
        result.isOrderEligible(),
        result.getDenialCode(),
        result.getAsOf()
    );
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
