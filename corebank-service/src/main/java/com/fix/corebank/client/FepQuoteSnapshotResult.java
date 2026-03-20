package com.fix.corebank.client;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepQuoteSourceMode;
import java.time.Instant;

public record FepQuoteSnapshotResult(
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

  public static FepQuoteSnapshotResult fromResponse(FepGatewayQuoteSnapshotResponse response) {
    if (response == null) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "latest quote snapshot response is required"
      );
    }
    requireText(response.quoteSnapshotId(), "quoteSnapshotId is required in latest quote snapshot response");
    requireText(response.symbol(), "symbol is required in latest quote snapshot response");
    if (response.quoteSourceMode() == null) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "quoteSourceMode is required in latest quote snapshot response"
      );
    }
    if (response.quoteAsOf() == null) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "quoteAsOf is required in latest quote snapshot response"
      );
    }
    return new FepQuoteSnapshotResult(
        response.quoteSnapshotId(),
        response.symbol(),
        response.quoteSourceMode(),
        response.quoteAsOf(),
        response.bestBid(),
        response.bestAsk(),
        response.lastTrade(),
        response.streamOffset(),
        response.stale()
    );
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, message);
    }
  }
}
