package com.fix.fepgateway.vo;

import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import java.time.Instant;

public record GatewayExecutionOutcome(
    String fepOrderId,
    FepExecType execType,
    FepOrdStatus ordStatus,
    Long executedQty,
    Long executedPrice,
    Long leavesQty,
    Instant transactTime
) {
}
