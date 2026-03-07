package com.fix.fepgateway.dto.response;

import com.fix.fepgateway.vo.GatewayOrderResult;

public class FepOrderResponse {

  private final String clOrdId;
  private final String status;
  private final String plane;

  private FepOrderResponse(String clOrdId, String status, String plane) {
    this.clOrdId = clOrdId;
    this.status = status;
    this.plane = plane;
  }

  public static FepOrderResponse from(GatewayOrderResult result) {
    return new FepOrderResponse(result.getClOrdId(), result.getStatus(), result.getPlane());
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
