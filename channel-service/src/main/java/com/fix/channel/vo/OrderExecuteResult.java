package com.fix.channel.vo;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderExecuteResult {

  private final Long orderId;
  private final String clOrdId;
  private final String status;
  private final boolean idempotent;
  private final BigDecimal orderQuantity;
  private final String executionResult;
  private final BigDecimal executedQty;
  private final BigDecimal leavesQty;
  private final BigDecimal executedPrice;
  private final String externalOrderId;
  private final String externalSyncStatus;
  private final Instant executedAt;

  private OrderExecuteResult(
      Long orderId,
      String clOrdId,
      String status,
      boolean idempotent,
      BigDecimal orderQuantity,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Instant executedAt
  ) {
    this.orderId = orderId;
    this.clOrdId = clOrdId;
    this.status = status;
    this.idempotent = idempotent;
    this.orderQuantity = orderQuantity;
    this.executionResult = executionResult;
    this.executedQty = executedQty;
    this.leavesQty = leavesQty;
    this.executedPrice = executedPrice;
    this.externalOrderId = externalOrderId;
    this.externalSyncStatus = externalSyncStatus;
    this.executedAt = executedAt;
  }

  public static OrderExecuteResult of(
      Long orderId,
      String clOrdId,
      String status,
      boolean idempotent,
      BigDecimal orderQuantity
  ) {
    return new OrderExecuteResult(orderId, clOrdId, status, idempotent, orderQuantity, null, null, null, null, null, null, null);
  }

  public static OrderExecuteResult of(
      Long orderId,
      String clOrdId,
      String status,
      boolean idempotent,
      BigDecimal orderQuantity,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Instant executedAt
  ) {
    return new OrderExecuteResult(
        orderId,
        clOrdId,
        status,
        idempotent,
        orderQuantity,
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        externalSyncStatus,
        executedAt
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

  public String getExternalSyncStatus() {
    return externalSyncStatus;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }
}
