package com.fix.fepgateway.vo;

import java.math.BigDecimal;

public class GatewayOrderSubmitCommand {

  private final String clOrdId;
  private final String symbol;
  private final String side;
  private final BigDecimal qty;

  private GatewayOrderSubmitCommand(String clOrdId, String symbol, String side, BigDecimal qty) {
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.qty = qty;
  }

  public static GatewayOrderSubmitCommand of(String clOrdId, String symbol, String side, BigDecimal qty) {
    return new GatewayOrderSubmitCommand(clOrdId, symbol, side, qty);
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

  public BigDecimal getQty() {
    return qty;
  }
}
