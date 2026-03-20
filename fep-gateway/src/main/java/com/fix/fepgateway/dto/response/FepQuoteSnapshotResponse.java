package com.fix.fepgateway.dto.response;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.vo.GatewayQuoteSnapshotResult;
import java.time.Instant;

public record FepQuoteSnapshotResponse(
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

  public static FepQuoteSnapshotResponse from(GatewayQuoteSnapshotResult result) {
    return new FepQuoteSnapshotResponse(
        result.quoteSnapshotId(),
        result.symbol(),
        result.quoteSourceMode(),
        result.quoteAsOf(),
        result.bestBid(),
        result.bestAsk(),
        result.lastTrade(),
        result.streamOffset(),
        result.stale()
    );
  }
}
