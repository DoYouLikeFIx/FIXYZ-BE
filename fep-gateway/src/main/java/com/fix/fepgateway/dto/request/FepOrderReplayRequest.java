package com.fix.fepgateway.dto.request;

import com.fix.fepgateway.vo.GatewayOrderReplayCommand;
import jakarta.validation.constraints.NotBlank;

public class FepOrderReplayRequest {

  @NotBlank
  private String clOrdId;

  @NotBlank
  private String reason;

  public GatewayOrderReplayCommand toVo(String pathClOrdId) {
    return GatewayOrderReplayCommand.of(pathClOrdId, reason);
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
