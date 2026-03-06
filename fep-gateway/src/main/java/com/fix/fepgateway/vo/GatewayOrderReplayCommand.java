package com.fix.fepgateway.vo;

public class GatewayOrderReplayCommand {

  private final String clOrdId;
  private final String reason;

  private GatewayOrderReplayCommand(String clOrdId, String reason) {
    this.clOrdId = clOrdId;
    this.reason = reason;
  }

  public static GatewayOrderReplayCommand of(String clOrdId, String reason) {
    return new GatewayOrderReplayCommand(clOrdId, reason);
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getReason() {
    return reason;
  }
}
