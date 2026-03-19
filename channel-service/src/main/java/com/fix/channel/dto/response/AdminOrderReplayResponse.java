package com.fix.channel.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.channel.vo.AdminOrderReplayResult;
import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminOrderReplayResponse(
    String clOrdId,
    String finalStatus,
    String executionResult,
    String executionSource,
    BigDecimal executedQty,
    BigDecimal executedPrice,
    String processedBy,
    Instant processedAt
) {

  public static AdminOrderReplayResponse from(AdminOrderReplayResult result) {
    return new AdminOrderReplayResponse(
        result.getClOrdId(),
        result.getFinalStatus(),
        result.getExecutionResult(),
        result.getExecutionSource(),
        result.getExecutedQty(),
        result.getExecutedPrice(),
        result.getProcessedBy(),
        result.getProcessedAt()
    );
  }
}
