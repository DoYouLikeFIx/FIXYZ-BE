package com.fix.fepgateway.vo;

public class GatewayOrderResult {

  private final String clOrdId;
  private final String status;
  private final String plane;

  private GatewayOrderResult(String clOrdId, String status, String plane) {
    this.clOrdId = clOrdId;
    this.status = status;
    this.plane = plane;
  }

  public static GatewayOrderResult of(String clOrdId, String status, String plane) {
    return new GatewayOrderResult(clOrdId, status, plane);
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getStatus() {
    return status;
  }

  public String getPlane() {
    return plane;
  }
}
