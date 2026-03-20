package com.fix.corebank.service;

public record QuoteFreshnessDecision(
    boolean fresh,
    long snapshotAgeMs,
    long maxQuoteAgeMs
) {

  public boolean stale() {
    return !fresh;
  }
}
