package com.fix.fepsimulator.vo;

import java.util.Arrays;
import java.util.Optional;

public enum ChaosRuleAction {
  APPROVE,
  DECLINE,
  IGNORE,
  DISCONNECT,
  MALFORMED_RESP,
  TIMEOUT;

  public static Optional<ChaosRuleAction> from(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(candidate -> candidate.name().equalsIgnoreCase(value.trim()))
        .findFirst();
  }
}