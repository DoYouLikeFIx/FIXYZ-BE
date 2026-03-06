package com.fix.fepgateway.dto.request;

import com.fix.fepgateway.vo.GatewayInternalOrderStatusCommand;
import jakarta.validation.constraints.NotBlank;

public class FepInternalOrderStatusRequest {

  @NotBlank
  private String status;

  public GatewayInternalOrderStatusCommand toVo(String clOrdId) {
    return GatewayInternalOrderStatusCommand.of(clOrdId, status);
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
