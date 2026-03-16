package com.fix.corebank.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.corebank.vo.InternalOrderResult;
import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalOrderResponse {

  private final Long orderId;
  private final String clOrdId;
  private final String status;
  private final String externalSyncStatus;
  private final boolean idempotent;
  private final BigDecimal orderQuantity;
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

  private InternalOrderResponse(
      Long orderId,
      String clOrdId,
      String status,
      String externalSyncStatus,
      boolean idempotent,
      BigDecimal orderQuantity,
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
    this.idempotent = idempotent;
    this.orderQuantity = orderQuantity;
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

  public static InternalOrderResponse from(InternalOrderResult result) {
    return new InternalOrderResponse(
        result.getOrderId(),
        result.getClOrdId(),
        result.getStatus(),
        result.getExternalSyncStatus(),
        result.isIdempotent(),
        result.getOrderQuantity(),
        result.getExecutionResult(),
        result.getExecutedQty(),
        result.getLeavesQty(),
        result.getExecutedPrice(),
        result.getExternalOrderId(),
        result.getExecutedAt(),
        result.getMessage(),
        result.getRetriable(),
        result.getEscalationRequired(),
        result.getAttemptCount(),
        result.getMaxRetryCount()
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

  public boolean isIdempotent() {
    return idempotent;
  }

  public BigDecimal getOrderQuantity() {
    return orderQuantity;
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
