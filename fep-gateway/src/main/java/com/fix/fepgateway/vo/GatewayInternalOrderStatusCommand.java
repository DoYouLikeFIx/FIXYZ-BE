package com.fix.fepgateway.vo;

public class GatewayInternalOrderStatusCommand {

  private final String clOrdId;
  private final String status;

  private GatewayInternalOrderStatusCommand(String clOrdId, String status) {
    this.clOrdId = clOrdId;
    this.status = status;
  }

  public static GatewayInternalOrderStatusCommand of(String clOrdId, String status) {
    return new GatewayInternalOrderStatusCommand(clOrdId, status);
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getStatus() {
    return status;
  }
}
