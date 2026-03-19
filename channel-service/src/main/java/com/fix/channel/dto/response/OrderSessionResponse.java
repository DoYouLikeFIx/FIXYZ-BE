package com.fix.channel.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fix.channel.serialization.OrderSessionResponseSerializer;
import com.fix.channel.vo.OrderSessionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@JsonSerialize(using = OrderSessionResponseSerializer.class)
public record OrderSessionResponse(
    String orderSessionId,
    String clOrdId,
    String status,
    boolean challengeRequired,
    String authorizationReason,
    Long accountId,
    String symbol,
    String side,
    String orderType,
    BigDecimal qty,
    BigDecimal price,
    String quoteSnapshotId,
    Instant quoteAsOf,
    String quoteSourceMode,
    BigDecimal preTradePrice,
    String executionResult,
    BigDecimal executedQty,
    BigDecimal leavesQty,
    BigDecimal executedPrice,
    String externalOrderId,
    @Schema(description = "External synchronization state reported by corebank.")
    String externalSyncStatus,
    @Schema(
        description = "Whether the execute call replayed an already-posted corebank order.",
        nullable = true
    )
    Boolean idempotent,
    String failureReason,
    Instant executedAt,
    Instant canceledAt,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt,
    Long remainingSeconds
) {

  public static OrderSessionResponse from(OrderSessionResult result) {
    ExecutionView executionView = ExecutionView.from(result);
    return new OrderSessionResponse(
        result.getOrderSessionId(),
        result.getClOrdId(),
        result.getStatus(),
        result.isChallengeRequired(),
        result.getAuthorizationReason(),
        result.getAccountId(),
        result.getSymbol(),
        result.getSide(),
        result.getOrderType(),
        result.getQty(),
        result.getPrice(),
        result.getQuoteSnapshotId(),
        result.getQuoteAsOf(),
        result.getQuoteSourceMode(),
        result.getPreTradePrice(),
        executionView.executionResult(),
        executionView.executedQty(),
        executionView.leavesQty(),
        executionView.executedPrice(),
        executionView.externalOrderId(),
        executionView.externalSyncStatus(),
        result.getIdempotent(),
        executionView.failureReason(),
        executionView.executedAt(),
        executionView.canceledAt(),
        result.getCreatedAt(),
        result.getUpdatedAt(),
        result.getExpiresAt(),
        result.getRemainingSeconds()
    );
  }

  private record ExecutionView(
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      String failureReason,
      Instant executedAt,
      Instant canceledAt
  ) {

    private static ExecutionView from(OrderSessionResult result) {
      if ("REQUERYING".equals(result.getStatus())) {
        return new ExecutionView(null, null, null, null, null, null, null, null, null);
      }
      if ("ESCALATED".equals(result.getStatus())) {
        return new ExecutionView(
            null,
            null,
            null,
            null,
            null,
            null,
            result.getFailureReason(),
            null,
            null
        );
      }
      return new ExecutionView(
          result.getExecutionResult(),
          result.getExecutedQty(),
          result.getLeavesQty(),
          result.getExecutedPrice(),
          result.getExternalOrderId(),
          result.getExternalSyncStatus(),
          result.getFailureReason(),
          result.getExecutedAt(),
          result.getCanceledAt()
      );
    }
  }
}
