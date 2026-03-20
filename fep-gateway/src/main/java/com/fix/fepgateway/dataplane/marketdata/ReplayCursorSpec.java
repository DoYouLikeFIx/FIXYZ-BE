package com.fix.fepgateway.dataplane.marketdata;

import java.math.BigDecimal;
import java.util.Objects;

public record ReplayCursorSpec(
    String replayId,
    String seed,
    String symbol,
    long cursorOffset,
    BigDecimal speedFactor
) {

  public ReplayCursorSpec {
    requireNonBlank(replayId, "replayId");
    requireNonBlank(seed, "seed");
    requireNonBlank(symbol, "symbol");
    if (cursorOffset < 0) {
      throw new IllegalArgumentException("cursorOffset must be zero or positive");
    }
    Objects.requireNonNull(speedFactor, "speedFactor must not be null");
    if (speedFactor.signum() <= 0) {
      throw new IllegalArgumentException("speedFactor must be greater than zero");
    }
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
