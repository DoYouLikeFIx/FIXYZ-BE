package com.fix.corebank.vo;

import java.math.BigDecimal;

public class PortfolioResult {

  private final Long accountId;
  private final String accountNo;
  private final String symbol;
  private final BigDecimal quantity;
  private final BigDecimal dailySellLimit;
  private final BigDecimal todaySellQuantity;

  private PortfolioResult(
      Long accountId,
      String accountNo,
      String symbol,
      BigDecimal quantity,
      BigDecimal dailySellLimit,
      BigDecimal todaySellQuantity
  ) {
    this.accountId = accountId;
    this.accountNo = accountNo;
    this.symbol = symbol;
    this.quantity = quantity;
    this.dailySellLimit = dailySellLimit;
    this.todaySellQuantity = todaySellQuantity;
  }

  public static PortfolioResult of(
      Long accountId,
      String accountNo,
      String symbol,
      BigDecimal quantity,
      BigDecimal dailySellLimit,
      BigDecimal todaySellQuantity
  ) {
    return new PortfolioResult(accountId, accountNo, symbol, quantity, dailySellLimit, todaySellQuantity);
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getAccountNo() {
    return accountNo;
  }

  public String getSymbol() {
    return symbol;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getDailySellLimit() {
    return dailySellLimit;
  }

  public BigDecimal getTodaySellQuantity() {
    return todaySellQuantity;
  }
}
