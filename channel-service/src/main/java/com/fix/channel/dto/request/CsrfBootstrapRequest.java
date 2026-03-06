package com.fix.channel.dto.request;

import com.fix.channel.vo.CsrfBootstrapCommand;

public class CsrfBootstrapRequest {

  private String clientRequestId;

  public CsrfBootstrapCommand toVo() {
    return CsrfBootstrapCommand.of(clientRequestId);
  }

  public String getClientRequestId() {
    return clientRequestId;
  }

  public void setClientRequestId(String clientRequestId) {
    this.clientRequestId = clientRequestId;
  }
}
