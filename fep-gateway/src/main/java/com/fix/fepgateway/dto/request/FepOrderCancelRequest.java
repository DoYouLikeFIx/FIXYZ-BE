package com.fix.fepgateway.dto.request;

import com.fix.fepgateway.vo.GatewayOrderCancelCommand;
import jakarta.validation.constraints.NotBlank;

public class FepOrderCancelRequest {

  @NotBlank
  private String clOrdId;

  @NotBlank
  private String reason;

  public GatewayOrderCancelCommand toVo(String pathClOrdId) {
    return GatewayOrderCancelCommand.of(pathClOrdId, reason);
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public void setClOrdId(String clOrdId) {
    this.clOrdId = clOrdId;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}
