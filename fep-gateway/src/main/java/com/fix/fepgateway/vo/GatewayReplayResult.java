package com.fix.fepgateway.vo;

import com.fix.common.fep.FepExecutionResult;
import com.fix.common.fep.FepReplayExecutionSource;
import com.fix.common.fep.FepReplayFinalStatus;
import java.time.Instant;

public record GatewayReplayResult(
    String clOrdId,
    FepReplayFinalStatus finalStatus,
    FepExecutionResult executionResult,
    FepReplayExecutionSource executionSource,
    Long executedQty,
    Long executedPrice,
    String processedBy,
    Instant processedAt
) {
}
