package com.fix.fepgateway.dto.response;

import com.fix.fepgateway.vo.GatewayReplayTimelineResult;
import java.math.BigDecimal;

public record FepReplayTimelineResponse(
    String replayId,
    String symbol,
    String seed,
    Long cursorOffset,
    BigDecimal speedFactor,
    String status,
    Long emittedCount,
    String sequenceHash
) {

  public static FepReplayTimelineResponse from(GatewayReplayTimelineResult result) {
    return new FepReplayTimelineResponse(
        result.replayId(),
        result.symbol(),
        result.seed(),
        result.cursorOffset(),
        result.speedFactor(),
        result.status(),
        result.emittedCount(),
        result.sequenceHash()
    );
  }
}
