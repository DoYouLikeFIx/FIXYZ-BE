package com.fix.fepgateway.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.fepgateway.vo.GatewayOrderResult;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FepOrderResponse(
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

  public static FepOrderResponse from(GatewayOrderResult result) {
    return new FepOrderResponse(
        result.clOrdId(),
        result.fepOrderId(),
        result.execType(),
        result.ordStatus(),
        result.executedQty(),
        result.executedPrice(),
        result.leavesQty(),
        result.transactTime(),
        result.queryTime(),
        result.message(),
        result.rejectReason(),
        result.canceledQty(),
        result.parseError()
    );
  }
}
