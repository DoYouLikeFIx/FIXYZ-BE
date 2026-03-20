package com.fix.fepgateway.vo;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.entity.QuoteSnapshot;
import java.time.Instant;

public record GatewayQuoteSnapshotResult(
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

  public static GatewayQuoteSnapshotResult from(QuoteSnapshot snapshot) {
    return new GatewayQuoteSnapshotResult(
        snapshot.getQuoteSnapshotId(),
        snapshot.getSymbol(),
        snapshot.getSourceMode(),
        snapshot.getQuoteAsOf(),
        snapshot.getBestBid(),
        snapshot.getBestAsk(),
        snapshot.getLastTrade(),
        snapshot.getStreamOffset(),
        snapshot.isStale()
    );
  }
}
