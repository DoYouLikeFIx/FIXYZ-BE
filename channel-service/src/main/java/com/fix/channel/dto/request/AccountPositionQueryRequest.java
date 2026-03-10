package com.fix.channel.dto.request;

import com.fix.channel.vo.AccountPositionQueryCommand;
import jakarta.validation.constraints.NotBlank;

public class AccountPositionQueryRequest {

  @NotBlank
  private String symbol;

  public AccountPositionQueryCommand toVo(Long accountId, Long memberId) {
    return AccountPositionQueryCommand.of(accountId, memberId, symbol);
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }
}
