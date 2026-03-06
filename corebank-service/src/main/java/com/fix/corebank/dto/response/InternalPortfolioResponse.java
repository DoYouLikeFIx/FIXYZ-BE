package com.fix.corebank.dto.response;

import com.fix.corebank.vo.PortfolioResult;
import java.math.BigDecimal;

public class InternalPortfolioResponse {

  private final Long accountId;
  private final String accountNo;
  private final String symbol;
  private final BigDecimal quantity;
  private final BigDecimal dailySellLimit;
  private final BigDecimal todaySellQuantity;

  private InternalPortfolioResponse(
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

  public static InternalPortfolioResponse from(PortfolioResult result) {
    return new InternalPortfolioResponse(
        result.getAccountId(),
        result.getAccountNo(),
        result.getSymbol(),
        result.getQuantity(),
        result.getDailySellLimit(),
        result.getTodaySellQuantity()
    );
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
