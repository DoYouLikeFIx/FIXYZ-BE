package com.fix.fepgateway.vo;

import java.math.BigDecimal;

public record GatewayReplayTimelineResult(
    String replayId,
    String symbol,
    String seed,
    Long cursorOffset,
    BigDecimal speedFactor,
    String status,
    Long emittedCount,
    String sequenceHash
) {
}
