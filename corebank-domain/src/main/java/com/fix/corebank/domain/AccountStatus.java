package com.fix.corebank.domain;

import java.util.Locale;

public enum AccountStatus {
  ACTIVE(true),
  FROZEN(false),
  CLOSED(false);

  private final boolean orderEligible;

  AccountStatus(boolean orderEligible) {
    this.orderEligible = orderEligible;
  }

  public boolean isOrderEligible() {
    return orderEligible;
  }

  public static AccountStatus from(String rawStatus) {
    if (rawStatus == null || rawStatus.isBlank()) {
      throw new IllegalArgumentException("account status is required");
    }
    return AccountStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
  }
}
