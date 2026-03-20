package com.fix.fepgateway.dataplane.marketdata;

import com.fix.common.fep.FepQuoteSourceMode;
import java.time.Instant;
import java.util.Objects;

/**
 * `streamOffset` is the monotonic provider-relative sequence assigned after frame split/decrypt.
 * Quote snapshot IDs are generated from this offset plus provider, symbol, source mode, and quoteAsOf.
 */
public record NormalizedQuoteEvent(
    String provider,
    String symbol,
    FepQuoteSourceMode sourceMode,
    Instant quoteAsOf,
    Long bestBid,
    Long bestAsk,
    Long lastTrade,
    long streamOffset,
    boolean stale
) {

  public NormalizedQuoteEvent {
    requireNonBlank(provider, "provider");
    requireNonBlank(symbol, "symbol");
    Objects.requireNonNull(sourceMode, "sourceMode must not be null");
    Objects.requireNonNull(quoteAsOf, "quoteAsOf must not be null");
    if (streamOffset < 0) {
      throw new IllegalArgumentException("streamOffset must be zero or positive");
    }
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
