package com.fix.channel.vo;

public class CsrfBootstrapCommand {

  private final String clientRequestId;

  private CsrfBootstrapCommand(String clientRequestId) {
    this.clientRequestId = clientRequestId;
  }

  public static CsrfBootstrapCommand of(String clientRequestId) {
    return new CsrfBootstrapCommand(clientRequestId);
  }

  public String getClientRequestId() {
    return clientRequestId;
  }
}
