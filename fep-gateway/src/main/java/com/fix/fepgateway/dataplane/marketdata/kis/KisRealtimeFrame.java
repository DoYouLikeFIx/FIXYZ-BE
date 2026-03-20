package com.fix.fepgateway.dataplane.marketdata.kis;

public record KisRealtimeFrame(
    boolean encrypted,
    String trId,
    int recordCount,
    String payload
) {

  public KisRealtimeFrame {
    if (trId == null || trId.isBlank()) {
      throw new IllegalArgumentException("trId must not be blank");
    }
    if (recordCount < 1) {
      throw new IllegalArgumentException("recordCount must be greater than zero");
    }
    if (payload == null || payload.isBlank()) {
      throw new IllegalArgumentException("payload must not be blank");
    }
  }
}
