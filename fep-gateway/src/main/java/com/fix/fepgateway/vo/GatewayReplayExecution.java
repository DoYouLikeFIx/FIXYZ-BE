package com.fix.fepgateway.vo;

import com.fix.common.fep.FepExecutionResult;
import com.fix.common.fep.FepReplayExecutionSource;
import com.fix.common.fep.FepReplayFinalStatus;

public record GatewayReplayExecution(
    GatewayExecutionOutcome outcome,
    FepReplayFinalStatus finalStatus,
    FepExecutionResult executionResult,
    FepReplayExecutionSource executionSource
) {
}
