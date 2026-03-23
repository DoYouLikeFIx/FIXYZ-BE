package com.fix.fepgateway.dataplane.marketdata;

import java.time.Instant;

public record MarketDataSubscriptionProgress(
    Long lastEventOffset,
    Instant lastQuoteAsOf
) {
}
