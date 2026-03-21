package com.fix.channel.vo;

import com.fix.common.fep.FepQuoteSourceMode;
import java.math.BigDecimal;
import java.time.Instant;

public class OrderExecuteCommand {

  private final Long accountId;
  private final String clOrdId;
  private final String symbol;
  private final String side;
  private final String orderType;
  private final BigDecimal quantity;
  private final BigDecimal price;
  private final String quoteSnapshotId;
  private final Instant quoteAsOf;
  private final FepQuoteSourceMode quoteSourceMode;
  private final BigDecimal preTradePrice;

  private OrderExecuteCommand(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal quantity,
      BigDecimal price,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode,
      BigDecimal preTradePrice
  ) {
    this.accountId = accountId;
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.orderType = orderType;
    this.quantity = quantity;
    this.price = price;
    this.quoteSnapshotId = quoteSnapshotId;
    this.quoteAsOf = quoteAsOf;
    this.quoteSourceMode = quoteSourceMode;
    this.preTradePrice = preTradePrice;
  }

  public static OrderExecuteCommand of(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal quantity,
      BigDecimal price
  ) {
    return new OrderExecuteCommand(accountId, clOrdId, symbol, side, null, quantity, price, null, null, null, null);
  }

  public static OrderExecuteCommand of(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal quantity,
      BigDecimal price,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode,
      BigDecimal preTradePrice
  ) {
    return new OrderExecuteCommand(
        accountId,
        clOrdId,
        symbol,
        side,
        orderType,
        quantity,
        price,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode,
        preTradePrice
    );
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

  public String getOrderType() {
    return orderType;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getQuoteSnapshotId() {
    return quoteSnapshotId;
  }

  public Instant getQuoteAsOf() {
    return quoteAsOf;
  }

  public FepQuoteSourceMode getQuoteSourceMode() {
    return quoteSourceMode;
  }

  public BigDecimal getPreTradePrice() {
    return preTradePrice;
  }
}
