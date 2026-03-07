package com.fix.corebank.vo;

import java.math.BigDecimal;

public class InternalOrderCreateCommand {

  private final Long accountId;
  private final String clOrdId;
  private final String symbol;
  private final String side;
  private final BigDecimal quantity;
  private final BigDecimal price;

  private InternalOrderCreateCommand(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal quantity,
      BigDecimal price
  ) {
    this.accountId = accountId;
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.quantity = quantity;
    this.price = price;
  }

  public static InternalOrderCreateCommand of(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal quantity,
      BigDecimal price
  ) {
    return new InternalOrderCreateCommand(accountId, clOrdId, symbol, side, quantity, price);
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getSide() {
    return side;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getPrice() {
    return price;
  }
}
