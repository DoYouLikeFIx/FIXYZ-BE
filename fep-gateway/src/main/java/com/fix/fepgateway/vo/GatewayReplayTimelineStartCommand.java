package com.fix.fepgateway.vo;

import java.math.BigDecimal;
import java.util.Objects;

public record GatewayReplayTimelineStartCommand(
    String symbol,
    String seed,
    long startOffset,
    BigDecimal speedFactor
) {

  public GatewayReplayTimelineStartCommand {
    requireNonBlank(symbol, "symbol");
    requireNonBlank(seed, "seed");
    if (startOffset < 0) {
      throw new IllegalArgumentException("startOffset must be zero or positive");
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
