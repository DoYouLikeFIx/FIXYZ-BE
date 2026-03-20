package com.fix.corebank.vo;

import java.math.BigDecimal;
import java.time.Instant;

public class InternalOrderReplayResult {

  private final String clOrdId;
  private final String finalStatus;
  private final String executionResult;
  private final String executionSource;
  private final BigDecimal executedQty;
  private final BigDecimal leavesQty;
  private final BigDecimal executedPrice;
  private final String externalOrderId;
  private final String externalSyncStatus;
  private final Instant executedAt;
  private final Instant canceledAt;
  private final String processedBy;
  private final Instant processedAt;

  private InternalOrderReplayResult(
      String clOrdId,
      String finalStatus,
      String executionResult,
      String executionSource,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Instant executedAt,
      Instant canceledAt,
      String processedBy,
      Instant processedAt
  ) {
    this.clOrdId = clOrdId;
    this.finalStatus = finalStatus;
    this.executionResult = executionResult;
    this.executionSource = executionSource;
    this.executedQty = executedQty;
    this.leavesQty = leavesQty;
    this.executedPrice = executedPrice;
    this.externalOrderId = externalOrderId;
    this.externalSyncStatus = externalSyncStatus;
    this.executedAt = executedAt;
    this.canceledAt = canceledAt;
    this.processedBy = processedBy;
    this.processedAt = processedAt;
  }

  public static InternalOrderReplayResult of(
      String clOrdId,
      String finalStatus,
      String executionResult,
      String executionSource,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Instant executedAt,
      Instant canceledAt,
      String processedBy,
      Instant processedAt
  ) {
    return new InternalOrderReplayResult(
        clOrdId,
        finalStatus,
        executionResult,
        executionSource,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        externalSyncStatus,
        executedAt,
        canceledAt,
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

  public Instant getCanceledAt() {
    return canceledAt;
  }

  public String getProcessedBy() {
    return processedBy;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
