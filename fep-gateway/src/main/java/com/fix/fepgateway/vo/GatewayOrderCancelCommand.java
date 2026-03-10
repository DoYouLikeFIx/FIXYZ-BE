package com.fix.fepgateway.vo;

import com.fix.common.fep.FepSide;

public class GatewayOrderCancelCommand {

  private final String clOrdId;
  private final String symbol;
  private final FepSide side;
  private final Long cancelQty;
  private final FepCancelReason reason;

  private GatewayOrderCancelCommand(
      String clOrdId,
      String symbol,
      FepSide side,
      Long cancelQty,
      FepCancelReason reason
  ) {
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.cancelQty = cancelQty;
    this.reason = reason;
  }

  public static GatewayOrderCancelCommand of(
      String clOrdId,
      String symbol,
      FepSide side,
      Long cancelQty,
      FepCancelReason reason
  ) {
    return new GatewayOrderCancelCommand(clOrdId, symbol, side, cancelQty, reason);
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getSymbol() {
    return symbol;
  }

  public FepSide getSide() {
    return side;
  }

  public Long getCancelQty() {
    return cancelQty;
  }

  public FepCancelReason getReason() {
    return reason;
  }
}
