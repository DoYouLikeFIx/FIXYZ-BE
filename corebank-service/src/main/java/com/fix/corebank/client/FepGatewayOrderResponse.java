package com.fix.corebank.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FepGatewayOrderResponse(
    String clOrdId,
    String fepOrderId,
    FepExecType execType,
    FepOrdStatus ordStatus,
    Long executedQty,
    Long executedPrice,
    Long leavesQty,
    Instant transactTime,
    Instant queryTime,
    String message,
    String rejectReason,
    Long canceledQty,
    String parseError
) {
}
