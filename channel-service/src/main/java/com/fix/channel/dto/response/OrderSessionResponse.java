package com.fix.channel.dto.response;

import com.fix.channel.vo.OrderSessionResult;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderSessionResponse(
    String orderSessionId,
    String clOrdId,
    String status,
    Long accountId,
    String symbol,
    String side,
    String orderType,
    BigDecimal qty,
    BigDecimal price,
    String executionResult,
    BigDecimal executedQty,
    BigDecimal leavesQty,
    BigDecimal executedPrice,
    String externalOrderId,
    String failureReason,
    Instant executedAt,
    Instant canceledAt,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt,
    Long remainingSeconds
) {

  public static OrderSessionResponse from(OrderSessionResult result) {
    return new OrderSessionResponse(
        result.getOrderSessionId(),
        result.getClOrdId(),
        result.getStatus(),
        result.getAccountId(),
        result.getSymbol(),
        result.getSide(),
        result.getOrderType(),
        result.getQty(),
        result.getPrice(),
        result.getExecutionResult(),
        result.getExecutedQty(),
        result.getLeavesQty(),
        result.getExecutedPrice(),
        result.getExternalOrderId(),
        result.getFailureReason(),
        result.getExecutedAt(),
        result.getCanceledAt(),
        result.getCreatedAt(),
        result.getUpdatedAt(),
        result.getExpiresAt(),
        result.getRemainingSeconds()
    );
  }
}
