package com.fix.corebank.dto.response;

import com.fix.corebank.vo.AccountProvisioningResult;
import java.time.Instant;

public class InternalPortfolioProvisioningResponse {

  private final Long accountId;
  private final String accountNumber;
  private final String status;
  private final boolean idempotent;
  private final Long memberId;
  private final Instant createdAt;

  private InternalPortfolioProvisioningResponse(
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

  public static InternalPortfolioProvisioningResponse from(AccountProvisioningResult result) {
    return new InternalPortfolioProvisioningResponse(
        result.getAccountId(),
        result.getAccountNumber(),
        result.getStatus(),
        result.isIdempotent(),
        result.getMemberId(),
        result.getCreatedAt()
    );
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
