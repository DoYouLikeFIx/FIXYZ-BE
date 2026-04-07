package com.fix.corebank.entity;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.entity.BaseTimeEntity;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order extends BaseTimeEntity {

  private static final int SCALE = 4;

  public static final String EXTERNAL_SYNC_CONFIRMED = "CONFIRMED";
  public static final String EXTERNAL_SYNC_FAILED = "FAILED";
  public static final String EXTERNAL_SYNC_ESCALATED = "ESCALATED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "cl_ord_id", nullable = false, unique = true, length = 64)
  private String clOrdId;

  @Column(name = "symbol", nullable = false, length = 32)
  private String symbol;

  @Column(name = "side", nullable = false, length = 8)
  private String side;

  @Column(name = "order_qty", nullable = false, precision = 19, scale = 4)
  private BigDecimal orderQty;

  @Column(name = "order_type", nullable = false, length = 16)
  private String orderType;

  @Column(name = "order_price", precision = 19, scale = 4)
  private BigDecimal orderPrice;

  @Column(name = "pre_trade_price", precision = 19, scale = 4)
  private BigDecimal preTradePrice;

  @Column(name = "quote_snapshot_id", length = 64)
  private String quoteSnapshotId;

  @Column(name = "quote_as_of")
  private Instant quoteAsOf;

  @Enumerated(EnumType.STRING)
  @Column(name = "quote_source_mode", length = 16)
  private FepQuoteSourceMode quoteSourceMode;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "external_sync_status", length = 20)
  private String externalSyncStatus;

  @Column(name = "fep_reference_id", length = 64)
  private String fepReferenceId;

  @Column(name = "failure_reason", length = 255)
  private String failureReason;

  @Column(name = "execution_result", length = 32)
  private String executionResult;

  @Column(name = "executed_qty", precision = 19, scale = 4)
  private BigDecimal executedQty;

  @Column(name = "leaves_qty", precision = 19, scale = 4)
  private BigDecimal leavesQty;

  @Column(name = "executed_price", precision = 19, scale = 4)
  private BigDecimal executedPrice;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  protected Order() {
  }

  private Order(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal orderQty,
      BigDecimal orderPrice,
      BigDecimal preTradePrice,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode,
      String status,
      String externalSyncStatus,
      String fepReferenceId,
      String failureReason,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      Instant executedAt,
      Instant requestedAt
  ) {
    this.accountId = accountId;
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.orderType = orderType;
    this.orderQty = orderQty;
    this.orderPrice = orderPrice;
    this.preTradePrice = preTradePrice;
    this.quoteSnapshotId = quoteSnapshotId;
    this.quoteAsOf = quoteAsOf;
    this.quoteSourceMode = quoteSourceMode;
    this.status = status;
    this.externalSyncStatus = externalSyncStatus;
    this.fepReferenceId = fepReferenceId;
    this.failureReason = failureReason;
    this.executionResult = executionResult;
    this.executedQty = executedQty;
    this.leavesQty = leavesQty;
    this.executedPrice = executedPrice;
    this.executedAt = executedAt;
    this.requestedAt = requestedAt;
  }

  public static Order accepted(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal orderQty,
      BigDecimal orderPrice
  ) {
    return accepted(accountId, clOrdId, symbol, side, "LIMIT", orderQty, orderPrice, null, null, null, null);
  }

  public static Order accepted(
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String orderType,
      BigDecimal orderQty,
      BigDecimal orderPrice,
      BigDecimal preTradePrice,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode
  ) {
    return new Order(
        accountId,
        clOrdId,
        symbol,
        side,
        orderType,
        orderQty,
        orderPrice,
        preTradePrice,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode,
        "NEW",
        null,
        null,
        null,
        null,
        BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP),
        orderQty == null ? null : orderQty.setScale(SCALE, RoundingMode.HALF_UP),
        null,
        null,
        Instant.now()
    );
  }

  public Long getId() {
    return id;
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getSide() {
    return side;
  }

  public BigDecimal getOrderQty() {
    return orderQty;
  }

  public String getOrderType() {
    return orderType;
  }

  public BigDecimal getOrderPrice() {
    return orderPrice;
  }

  public BigDecimal getPreTradePrice() {
    return preTradePrice;
  }

  public String getQuoteSnapshotId() {
    return quoteSnapshotId;
  }

  public Instant getQuoteAsOf() {
    return quoteAsOf;
  }

  public FepQuoteSourceMode getQuoteSourceMode() {
    return quoteSourceMode;
  }

  public String getStatus() {
    return status;
  }

  public String getExternalSyncStatus() {
    return externalSyncStatus;
  }

  public String getFepReferenceId() {
    return fepReferenceId;
  }

  public String getFailureReason() {
    return failureReason;
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

  public Instant getExecutedAt() {
    return executedAt;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public void updateState(String status, String externalSyncStatus, String fepReferenceId, String failureReason) {
    this.status = status;
    this.externalSyncStatus = externalSyncStatus;
    this.fepReferenceId = fepReferenceId;
    this.failureReason = failureReason;
  }

  public void updateStatus(String status) {
    updateState(status, externalSyncStatus, fepReferenceId, failureReason);
  }

  public void updateExecutionSummary(
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      Instant executedAt
  ) {
    this.executionResult = executionResult;
    this.executedQty = executedQty;
    this.leavesQty = leavesQty;
    this.executedPrice = executedPrice;
    this.executedAt = executedAt;
  }

  public void markResting(BigDecimal leavesQty) {
    this.status = "NEW";
    this.failureReason = null;
    this.executionResult = null;
    this.executedQty = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    this.leavesQty = normalizeNonNegative(leavesQty, "leaves quantity is required");
    this.executedPrice = null;
    this.executedAt = null;
  }

  public void completeExecution(
      String status,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      Instant executedAt
  ) {
    requireNonBlank(status, "order status is required");
    requireNonBlank(executionResult, "execution result is required");
    if (executedAt == null) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "executedAt is required");
    }

    this.status = status;
    this.failureReason = null;
    updateExecutionSummary(
        executionResult,
        normalizeNonNegative(executedQty, "executed quantity is required"),
        normalizeNonNegative(leavesQty, "leaves quantity is required"),
        normalizeNonNegative(executedPrice, "executed price is required"),
        executedAt
    );
  }

  private void requireNonBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, message);
    }
  }

  private BigDecimal normalizeNonNegative(BigDecimal value, String nullMessage) {
    if (value == null) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, nullMessage);
    }
    BigDecimal normalized = value.setScale(SCALE, RoundingMode.HALF_UP);
    if (normalized.signum() < 0) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "execution summary values cannot be negative");
    }
    return normalized;
  }
}
