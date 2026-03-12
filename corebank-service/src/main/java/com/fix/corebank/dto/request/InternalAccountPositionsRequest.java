package com.fix.corebank.dto.request;

import com.fix.corebank.vo.AccountPositionsQueryCommand;
import jakarta.validation.constraints.NotNull;

public class InternalAccountPositionsRequest {

  @NotNull
  private Long memberId;

  public AccountPositionsQueryCommand toVo(Long accountId) {
    return AccountPositionsQueryCommand.of(accountId, memberId);
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long memberId) {
    this.memberId = memberId;
  }
}
