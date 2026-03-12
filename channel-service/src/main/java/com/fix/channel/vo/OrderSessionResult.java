package com.fix.channel.vo;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderSessionResult {

  private final String orderSessionId;
  private final String clOrdId;
  private final String status;
  private final Long accountId;
  private final String symbol;
  private final String side;
  private final String orderType;
  private final BigDecimal qty;
  private final BigDecimal price;
  private final Instant expiresAt;
  private final Long remainingSeconds;
  private final String executionResult;
  private final BigDecimal executedQty;
  private final BigDecimal leavesQty;
  private final BigDecimal executedPrice;
  private final String externalOrderId;
  private final String failureReason;
  private final Instant executedAt;
  private final Instant canceledAt;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final boolean created;

  private OrderSessionResult(
      String orderSessionId,
      String clOrdId,
      String status,
      Long accountId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      Instant expiresAt,
      Long remainingSeconds,
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
      boolean created
  ) {
    this.orderSessionId = orderSessionId;
    this.clOrdId = clOrdId;
    this.status = status;
    this.accountId = accountId;
    this.symbol = symbol;
    this.side = side;
    this.orderType = orderType;
    this.qty = qty;
    this.price = price;
    this.expiresAt = expiresAt;
    this.remainingSeconds = remainingSeconds;
    this.executionResult = executionResult;
    this.executedQty = executedQty;
    this.leavesQty = leavesQty;
    this.executedPrice = executedPrice;
    this.externalOrderId = externalOrderId;
    this.failureReason = failureReason;
    this.executedAt = executedAt;
    this.canceledAt = canceledAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.created = created;
  }

  public static OrderSessionResult of(
      String orderSessionId,
      String clOrdId,
      String status,
      Long accountId,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      Instant expiresAt,
      Long remainingSeconds,
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
      boolean created
  ) {
    return new OrderSessionResult(
        orderSessionId,
        clOrdId,
        status,
        accountId,
        symbol,
        side,
        orderType,
        qty,
        price,
        expiresAt,
        remainingSeconds,
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
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
