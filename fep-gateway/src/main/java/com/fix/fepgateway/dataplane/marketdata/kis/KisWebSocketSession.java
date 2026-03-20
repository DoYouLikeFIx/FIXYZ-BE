package com.fix.fepgateway.dataplane.marketdata.kis;

public interface KisWebSocketSession {

  void sendText(String payload);

  void close();

  boolean isOpen();
}
