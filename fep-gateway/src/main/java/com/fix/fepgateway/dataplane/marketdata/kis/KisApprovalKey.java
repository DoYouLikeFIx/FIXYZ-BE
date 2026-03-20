package com.fix.fepgateway.dataplane.marketdata.kis;

import java.time.Instant;
import java.util.Objects;

public record KisApprovalKey(String value, Instant issuedAt) {

  public KisApprovalKey {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("approval key must not be blank");
    }
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
  }
}
