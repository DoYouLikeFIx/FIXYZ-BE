package com.fix.corebank.dto.request;

import com.fix.corebank.vo.PortfolioQueryCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InternalPortfolioRequest {

  @NotNull
  private Long accountId;

  @NotBlank
  private String symbol;

  public PortfolioQueryCommand toVo() {
    return PortfolioQueryCommand.of(accountId, symbol);
  }

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }
}
