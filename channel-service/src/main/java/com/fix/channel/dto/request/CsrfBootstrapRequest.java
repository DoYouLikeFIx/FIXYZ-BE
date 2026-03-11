package com.fix.channel.dto.request;

import com.fix.channel.vo.CsrfBootstrapCommand;

public record CsrfBootstrapRequest(String clientRequestId) {

  public CsrfBootstrapCommand toVo() {
    return CsrfBootstrapCommand.of(clientRequestId);
  }
}
