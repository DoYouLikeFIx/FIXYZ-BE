package com.fix.corebank.dto.request;

import com.fix.corebank.vo.AccountStatusQueryCommand;
import jakarta.validation.constraints.NotNull;

public class InternalAccountStatusRequest {

  @NotNull
  private Long memberId;

  public AccountStatusQueryCommand toVo(Long accountId) {
    return AccountStatusQueryCommand.of(accountId, memberId);
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long memberId) {
    this.memberId = memberId;
  }
}
