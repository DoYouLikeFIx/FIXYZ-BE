package com.fix.corebank.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.corebank.vo.InternalOrderReplayResult;
import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalOrderReplayResponse {

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

  private InternalOrderReplayResponse(
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

  public static InternalOrderReplayResponse from(InternalOrderReplayResult result) {
    return new InternalOrderReplayResponse(
        result.getClOrdId(),
        result.getFinalStatus(),
        result.getExecutionResult(),
        result.getExecutionSource(),
        result.getExecutedQty(),
        result.getLeavesQty(),
        result.getExecutedPrice(),
        result.getExternalOrderId(),
        result.getExternalSyncStatus(),
        result.getExecutedAt(),
        result.getCanceledAt(),
        result.getProcessedBy(),
        result.getProcessedAt()
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
