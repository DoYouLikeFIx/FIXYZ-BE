package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order extends BaseTimeEntity {

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

  @Column(name = "order_price", nullable = false, precision = 19, scale = 4)
  private BigDecimal orderPrice;

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
      BigDecimal orderQty,
      BigDecimal orderPrice,
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
    this.orderQty = orderQty;
    this.orderPrice = orderPrice;
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
    return new Order(
        accountId,
        clOrdId,
        symbol,
        side,
        orderQty,
        orderPrice,
        "ACCEPTED",
        null,
        null,
        null,
        null,
        null,
        null,
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

  public BigDecimal getOrderPrice() {
    return orderPrice;
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
}
