package com.fix.fepgateway.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.common.fep.FepExecutionResult;
import com.fix.common.fep.FepReplayExecutionSource;
import com.fix.common.fep.FepReplayFinalStatus;
import com.fix.fepgateway.vo.GatewayReplayResult;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FepOrderReplayResponse(
    String clOrdId,
    FepReplayFinalStatus finalStatus,
    FepExecutionResult executionResult,
    FepReplayExecutionSource executionSource,
    Long executedQty,
    Long executedPrice,
    String processedBy,
    Instant processedAt
) {

  public static FepOrderReplayResponse from(GatewayReplayResult result) {
    return new FepOrderReplayResponse(
        result.clOrdId(),
        result.finalStatus(),
        result.executionResult(),
        result.executionSource(),
        result.executedQty(),
        result.executedPrice(),
        result.processedBy(),
        result.processedAt()
    );
  }
}
