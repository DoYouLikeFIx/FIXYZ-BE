package com.fix.fepgateway.dataplane.marketdata;

import com.fix.common.fep.FepQuoteSourceMode;
import java.util.Objects;

public record MarketDataSubscriptionSpec(
    String subscriptionId,
    String provider,
    String symbol,
    FepQuoteSourceMode sourceMode,
    String trId,
    String trKey
) {

  public MarketDataSubscriptionSpec {
    requireNonBlank(subscriptionId, "subscriptionId");
    requireNonBlank(provider, "provider");
    requireNonBlank(symbol, "symbol");
    Objects.requireNonNull(sourceMode, "sourceMode must not be null");
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
