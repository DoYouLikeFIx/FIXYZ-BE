package com.fix.fepgateway.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.common.fep.FepCancelStatus;
import com.fix.fepgateway.vo.GatewayCancelResult;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FepOrderCancelResponse(
    String origClOrdId,
    String cancelClOrdId,
    FepCancelStatus status,
    Long executedQty,
    Long canceledQty,
    Long executedPrice,
    Instant executedAt,
    Instant canceledAt
) {

  public static FepOrderCancelResponse from(GatewayCancelResult result) {
    return new FepOrderCancelResponse(
        result.origClOrdId(),
        result.cancelClOrdId(),
        result.status(),
        result.executedQty(),
        result.canceledQty(),
        result.executedPrice(),
        result.executedAt(),
        result.canceledAt()
    );
  }
}
