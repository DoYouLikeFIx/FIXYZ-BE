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
        result.getExecutionResult(),
        result.getExecutedQty(),
        result.getLeavesQty(),
        result.getExecutedPrice(),
        result.getExternalOrderId(),
        result.getExternalSyncStatus(),
        result.getIdempotent(),
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
