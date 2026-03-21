package com.fix.fepgateway.dataplane.marketdata.replay;

import java.math.BigDecimal;

public record ReplayTimelineStatus(
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
