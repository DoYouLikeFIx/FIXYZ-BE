package com.fix.fepgateway.vo;

public class GatewayOrderCancelCommand {

  private final String clOrdId;
  private final String reason;

  private GatewayOrderCancelCommand(String clOrdId, String reason) {
    this.clOrdId = clOrdId;
    this.reason = reason;
  }

  public static GatewayOrderCancelCommand of(String clOrdId, String reason) {
    return new GatewayOrderCancelCommand(clOrdId, reason);
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getReason() {
    return reason;
  }
}
