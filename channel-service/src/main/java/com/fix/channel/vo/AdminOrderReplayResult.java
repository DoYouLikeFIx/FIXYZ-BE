package com.fix.channel.vo;

import java.math.BigDecimal;
import java.time.Instant;

public class AdminOrderReplayResult {

  private final String clOrdId;
  private final String finalStatus;
  private final String executionResult;
  private final String executionSource;
  private final BigDecimal executedQty;
  private final BigDecimal executedPrice;
  private final String processedBy;
  private final Instant processedAt;

  private AdminOrderReplayResult(
      String clOrdId,
      String finalStatus,
      String executionResult,
      String executionSource,
      BigDecimal executedQty,
      BigDecimal executedPrice,
      String processedBy,
      Instant processedAt
  ) {
    this.clOrdId = clOrdId;
    this.finalStatus = finalStatus;
    this.executionResult = executionResult;
    this.executionSource = executionSource;
    this.executedQty = executedQty;
    this.executedPrice = executedPrice;
    this.processedBy = processedBy;
    this.processedAt = processedAt;
  }

  public static AdminOrderReplayResult of(
      String clOrdId,
      String finalStatus,
      String executionResult,
      String executionSource,
      BigDecimal executedQty,
      BigDecimal executedPrice,
      String processedBy,
      Instant processedAt
  ) {
    return new AdminOrderReplayResult(
        clOrdId,
        finalStatus,
        executionResult,
        executionSource,
        executedQty,
        executedPrice,
        processedBy,
        processedAt
    );
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getFinalStatus() {
    return finalStatus;
  }

  public String getExecutionResult() {
    return executionResult;
  }

  public String getExecutionSource() {
    return executionSource;
  }

  public BigDecimal getExecutedQty() {
    return executedQty;
  }

  public BigDecimal getExecutedPrice() {
    return executedPrice;
  }

  public String getProcessedBy() {
    return processedBy;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
