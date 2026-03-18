package com.fix.channel.vo;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderRequeryResult {

  private final Long orderId;
  private final String clOrdId;
  private final String status;
  private final String externalSyncStatus;
  private final String executionResult;
  private final BigDecimal executedQty;
  private final BigDecimal leavesQty;
  private final BigDecimal executedPrice;
  private final String externalOrderId;
  private final Instant executedAt;
  private final String message;
  private final Boolean retriable;
  private final Boolean escalationRequired;
  private final Integer attemptCount;
  private final Integer maxRetryCount;

  private OrderRequeryResult(
      Long orderId,
      String clOrdId,
      String status,
      String externalSyncStatus,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      Instant executedAt,
      String message,
      Boolean retriable,
      Boolean escalationRequired,
      Integer attemptCount,
      Integer maxRetryCount
  ) {
    this.orderId = orderId;
    this.clOrdId = clOrdId;
    this.status = status;
    this.externalSyncStatus = externalSyncStatus;
    this.executionResult = executionResult;
    this.executedQty = executedQty;
    this.leavesQty = leavesQty;
    this.executedPrice = executedPrice;
    this.externalOrderId = externalOrderId;
    this.executedAt = executedAt;
    this.message = message;
    this.retriable = retriable;
    this.escalationRequired = escalationRequired;
    this.attemptCount = attemptCount;
    this.maxRetryCount = maxRetryCount;
  }

  public static OrderRequeryResult of(
      Long orderId,
      String clOrdId,
      String status,
      String externalSyncStatus,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      Instant executedAt,
      String message,
      Boolean retriable,
      Boolean escalationRequired,
      Integer attemptCount,
      Integer maxRetryCount
  ) {
    return new OrderRequeryResult(
        orderId,
        clOrdId,
        status,
        externalSyncStatus,
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        executedAt,
        message,
        retriable,
        escalationRequired,
        attemptCount,
        maxRetryCount
    );
  }

  public Long getOrderId() {
    return orderId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getStatus() {
    return status;
  }

  public String getExternalSyncStatus() {
    return externalSyncStatus;
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

  public Instant getExecutedAt() {
    return executedAt;
  }

  public String getMessage() {
    return message;
  }

  public Boolean getRetriable() {
    return retriable;
  }

  public Boolean getEscalationRequired() {
    return escalationRequired;
  }

  public Integer getAttemptCount() {
    return attemptCount;
  }

  public Integer getMaxRetryCount() {
    return maxRetryCount;
  }
}
