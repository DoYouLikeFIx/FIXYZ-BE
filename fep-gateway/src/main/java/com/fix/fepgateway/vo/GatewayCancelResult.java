package com.fix.fepgateway.vo;

import com.fix.common.fep.FepCancelStatus;
import java.time.Instant;

public record GatewayCancelResult(
    String origClOrdId,
    String cancelClOrdId,
    FepCancelStatus status,
    Long executedQty,
    Long canceledQty,
    Long executedPrice,
    Instant executedAt,
    Instant canceledAt
) {
}
