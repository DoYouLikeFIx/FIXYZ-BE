package com.fix.fepgateway.vo;

public class GatewayOrderStatusCommand {

  private final String clOrdId;

  private GatewayOrderStatusCommand(String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public static GatewayOrderStatusCommand of(String clOrdId) {
    return new GatewayOrderStatusCommand(clOrdId);
  }

  public String getClOrdId() {
    return clOrdId;
  }
}
