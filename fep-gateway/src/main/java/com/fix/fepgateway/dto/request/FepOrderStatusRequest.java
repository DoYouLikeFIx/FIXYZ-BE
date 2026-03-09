package com.fix.fepgateway.dto.request;

import com.fix.fepgateway.vo.GatewayOrderStatusCommand;

public record FepOrderStatusRequest() {

  public GatewayOrderStatusCommand toVo(String clOrdId) {
    return GatewayOrderStatusCommand.of(clOrdId);
  }
}
