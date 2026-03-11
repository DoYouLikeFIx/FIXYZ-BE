package com.fix.channel.vo;

import java.math.BigDecimal;
import java.time.Instant;

public class AccountOrderHistoryItemResult {

  private final String symbol;
  private final String symbolName;
  private final String side;
  private final BigDecimal qty;
  private final BigDecimal unitPrice;
  private final BigDecimal totalAmount;
  private final String status;
  private final String clOrdId;
  private final Instant createdAt;

  private AccountOrderHistoryItemResult(
      String symbol,
      String symbolName,
      String side,
      BigDecimal qty,
      BigDecimal unitPrice,
      BigDecimal totalAmount,
      String status,
      String clOrdId,
      Instant createdAt
  ) {
    this.symbol = symbol;
    this.symbolName = symbolName;
    this.side = side;
    this.qty = qty;
    this.unitPrice = unitPrice;
    this.totalAmount = totalAmount;
    this.status = status;
    this.clOrdId = clOrdId;
    this.createdAt = createdAt;
  }

  public static AccountOrderHistoryItemResult of(
      String symbol,
      String symbolName,
      String side,
      BigDecimal qty,
      BigDecimal unitPrice,
      BigDecimal totalAmount,
      String status,
      String clOrdId,
      Instant createdAt
  ) {
    return new AccountOrderHistoryItemResult(
        symbol,
        symbolName,
        side,
        qty,
        unitPrice,
        totalAmount,
        status,
        clOrdId,
        createdAt
    );
  }

  public String getSymbol() {
    return symbol;
  }

  public String getSymbolName() {
    return symbolName;
  }

  public String getSide() {
    return side;
  }

  public BigDecimal getQty() {
    return qty;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getStatus() {
    return status;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
