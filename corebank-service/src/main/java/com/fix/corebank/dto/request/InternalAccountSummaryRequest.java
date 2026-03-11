package com.fix.corebank.dto.request;

import com.fix.corebank.vo.AccountSummaryQueryCommand;
import jakarta.validation.constraints.NotNull;

public class InternalAccountSummaryRequest {

  @NotNull
  private Long memberId;

  public AccountSummaryQueryCommand toVo(Long accountId) {
    return AccountSummaryQueryCommand.of(accountId, memberId);
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long memberId) {
    this.memberId = memberId;
  }
}
