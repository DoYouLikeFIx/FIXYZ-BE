package com.fix.fepgateway.dataplane.marketdata.kis;

public record KisWebSocketControlMessage(
    String trId,
    String message,
    KisDecryptionContext decryptionContext
) {

  public KisWebSocketControlMessage {
    if (trId == null || trId.isBlank()) {
      throw new IllegalArgumentException("trId must not be blank");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
  }

  public boolean isSubscribeSuccess() {
    return "SUBSCRIBE SUCCESS".equalsIgnoreCase(message);
  }

  public boolean hasDecryptionContext() {
    return decryptionContext != null;
  }
}
