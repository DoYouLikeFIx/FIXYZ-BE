package com.fix.channel.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSessionResult(
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
    Instant expiresAt,
    Long remainingSeconds,
    String executionResult,
    BigDecimal executedQty,
    BigDecimal leavesQty,
    BigDecimal executedPrice,
    String externalOrderId,
    String externalSyncStatus,
    Boolean idempotent,
    String failureReason,
    Instant executedAt,
    Instant canceledAt,
    Instant createdAt,
    Instant updatedAt,
    boolean created
) {

  public static OrderSessionResult of(
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
      Instant expiresAt,
      Long remainingSeconds,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      String failureReason,
      Instant executedAt,
      Instant canceledAt,
      Instant createdAt,
      Instant updatedAt,
      boolean created
  ) {
    return of(
        orderSessionId,
        clOrdId,
        status,
        challengeRequired,
        authorizationReason,
        accountId,
        symbol,
        side,
        orderType,
        qty,
        price,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode,
        preTradePrice,
        expiresAt,
        remainingSeconds,
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        externalSyncStatus,
        null,
        failureReason,
        executedAt,
        canceledAt,
        createdAt,
        updatedAt,
        created
    );
  }

  public static OrderSessionResult of(
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
      Instant expiresAt,
      Long remainingSeconds,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Boolean idempotent,
      String failureReason,
      Instant executedAt,
      Instant canceledAt,
      Instant createdAt,
      Instant updatedAt,
      boolean created
  ) {
    return new OrderSessionResult(
        orderSessionId,
        clOrdId,
        status,
        challengeRequired,
        authorizationReason,
        accountId,
        symbol,
        side,
        orderType,
        qty,
        price,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode,
        preTradePrice,
        expiresAt,
        remainingSeconds,
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        externalSyncStatus,
        idempotent,
        failureReason,
        executedAt,
        canceledAt,
        createdAt,
        updatedAt,
        created
    );
  }

  public String getOrderSessionId() {
    return orderSessionId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getStatus() {
    return status;
  }

  public boolean isChallengeRequired() {
    return challengeRequired;
  }

  public String getAuthorizationReason() {
    return authorizationReason;
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getSide() {
    return side;
  }

  public String getOrderType() {
    return orderType;
  }

  public BigDecimal getQty() {
    return qty;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getQuoteSnapshotId() {
    return quoteSnapshotId;
  }

  public Instant getQuoteAsOf() {
    return quoteAsOf;
  }

  public String getQuoteSourceMode() {
    return quoteSourceMode;
  }

  public BigDecimal getPreTradePrice() {
    return preTradePrice;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Long getRemainingSeconds() {
    return remainingSeconds;
  }

  public String getExecutionResult() {
    return executionResult;
  }

  public BigDecimal getExecutedQty() {
    return executedQty;
  }

  public BigDecimal getLeavesQty() {
    return leavesQty;
  }

  public BigDecimal getExecutedPrice() {
    return executedPrice;
  }

  public String getExternalOrderId() {
    return externalOrderId;
  }

  public String getExternalSyncStatus() {
    return externalSyncStatus;
  }

  public Boolean getIdempotent() {
    return idempotent;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public Instant getCanceledAt() {
    return canceledAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public boolean isCreated() {
    return created;
  }
}
