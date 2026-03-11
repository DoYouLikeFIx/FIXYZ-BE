package com.fix.corebank.dto.request;

import com.fix.corebank.vo.AccountPositionQueryCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InternalAccountPositionRequest {

  @NotNull
  private Long memberId;

  @NotBlank
  private String symbol;

  public AccountPositionQueryCommand toVo(Long accountId) {
    return AccountPositionQueryCommand.of(accountId, memberId, symbol);
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long memberId) {
    this.memberId = memberId;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }
}

