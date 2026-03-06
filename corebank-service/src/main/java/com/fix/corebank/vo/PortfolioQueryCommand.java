package com.fix.corebank.vo;

public class PortfolioQueryCommand {

  private final Long accountId;
  private final String symbol;

  private PortfolioQueryCommand(Long accountId, String symbol) {
    this.accountId = accountId;
    this.symbol = symbol;
  }

  public static PortfolioQueryCommand of(Long accountId, String symbol) {
    return new PortfolioQueryCommand(accountId, symbol);
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getSymbol() {
    return symbol;
  }
}
