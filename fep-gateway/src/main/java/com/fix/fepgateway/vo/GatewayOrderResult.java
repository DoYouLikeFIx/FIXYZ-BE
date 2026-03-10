package com.fix.fepgateway.vo;

import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import java.time.Instant;

public record GatewayOrderResult(
    String clOrdId,
    String fepOrderId,
    FepExecType execType,
    FepOrdStatus ordStatus,
    Long executedQty,
    Long executedPrice,
    Long leavesQty,
    Instant transactTime,
    Instant queryTime,
    String message
) {
}
