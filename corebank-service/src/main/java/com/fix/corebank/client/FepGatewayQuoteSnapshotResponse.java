package com.fix.corebank.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fix.common.fep.FepQuoteSourceMode;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FepGatewayQuoteSnapshotResponse(
    String quoteSnapshotId,
    String symbol,
    FepQuoteSourceMode quoteSourceMode,
    Instant quoteAsOf,
    Long bestBid,
    Long bestAsk,
    Long lastTrade,
    Long streamOffset,
    boolean stale
) {
}
