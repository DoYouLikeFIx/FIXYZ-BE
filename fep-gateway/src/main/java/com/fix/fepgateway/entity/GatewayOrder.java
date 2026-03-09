package com.fix.fepgateway.entity;

import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.fepgateway.vo.GatewayExecutionOutcome;
import com.fix.fepgateway.vo.GatewayOrderResult;
import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "gateway_orders")
public class GatewayOrder extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cl_ord_id", nullable = false, unique = true, length = 64)
  private String clOrdId;

  @Column(name = "symbol", nullable = false, length = 32)
  private String symbol;

  @Column(name = "side", nullable = false, length = 8)
  private String side;

  @Column(name = "qty", nullable = false, precision = 19, scale = 4)
  private BigDecimal qty;

  @Column(name = "order_type", nullable = false, length = 16)
  private String orderType;

  @Column(name = "requested_price")
  private Long requestedPrice;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "fep_order_id", length = 96)
  private String fepOrderId;

  @Column(name = "exec_type", length = 32)
  private String execType;

  @Column(name = "executed_qty")
  private Long executedQty;

  @Column(name = "executed_price")
  private Long executedPrice;

  @Column(name = "leaves_qty")
  private Long leavesQty;

  @Column(name = "transact_time")
  private Instant transactTime;

  @Column(name = "transport", nullable = false, length = 16)
  private String transport;

  @Column(name = "recovery_status", nullable = false, length = 32)
  private String recoveryStatus;

  @Column(name = "cancel_failure_mode", nullable = false, length = 32)
  private String cancelFailureMode;

  @Column(name = "requery_ord_status", length = 32)
  private String requeryOrdStatus;

  @Column(name = "requery_executed_qty")
  private Long requeryExecutedQty;

  @Column(name = "requery_executed_price")
  private Long requeryExecutedPrice;

  protected GatewayOrder() {
  }

  private GatewayOrder(
      String clOrdId,
      String symbol,
      String side,
      BigDecimal qty,
      String orderType,
      Long requestedPrice,
      String status,
      String fepOrderId,
      String execType,
      Long executedQty,
      Long executedPrice,
      Long leavesQty,
      Instant transactTime,
      String transport,
      String recoveryStatus,
      String cancelFailureMode,
      String requeryOrdStatus,
      Long requeryExecutedQty,
      Long requeryExecutedPrice
  ) {
    this.clOrdId = clOrdId;
    this.symbol = symbol;
    this.side = side;
    this.qty = qty;
    this.orderType = orderType;
    this.requestedPrice = requestedPrice;
    this.status = status;
    this.fepOrderId = fepOrderId;
    this.execType = execType;
    this.executedQty = executedQty;
    this.executedPrice = executedPrice;
    this.leavesQty = leavesQty;
    this.transactTime = transactTime;
    this.transport = transport;
    this.recoveryStatus = recoveryStatus;
    this.cancelFailureMode = cancelFailureMode;
    this.requeryOrdStatus = requeryOrdStatus;
    this.requeryExecutedQty = requeryExecutedQty;
    this.requeryExecutedPrice = requeryExecutedPrice;
  }

  public static GatewayOrder received(
      String clOrdId,
      String symbol,
      String side,
      BigDecimal qty,
      String orderType,
      Long requestedPrice,
      String transport
  ) {
    return new GatewayOrder(
        clOrdId,
        symbol,
        side,
        qty,
        orderType,
        requestedPrice,
        FepOrdStatus.PENDING.name(),
        null,
        FepExecType.PENDING_NEW.name(),
        0L,
        null,
        qty.longValueExact(),
        null,
        transport,
        "ACTIVE",
        "NONE",
        null,
        null,
        null
    );
  }

  public Long getId() {
    return id;
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

  public BigDecimal getQty() {
    return qty;
  }

  public String getOrderType() {
    return orderType;
  }

  public Long getRequestedPrice() {
    return requestedPrice;
  }

  public String getStatus() {
    return status;
  }

  public String getTransport() {
    return transport;
  }

  public String getFepOrderId() {
    return fepOrderId;
  }

  public String getRecoveryStatus() {
    return recoveryStatus;
  }

  public String getCancelFailureMode() {
    return cancelFailureMode;
  }

  public String getRequeryOrdStatus() {
    return requeryOrdStatus;
  }

  public Long getRequeryExecutedQty() {
    return requeryExecutedQty;
  }

  public Long getRequeryExecutedPrice() {
    return requeryExecutedPrice;
  }

  public String getExecType() {
    return execType;
  }

  public Long getExecutedQty() {
    return executedQty;
  }

  public Long getExecutedPrice() {
    return executedPrice;
  }

  public Long getLeavesQty() {
    return leavesQty;
  }

  public Instant getTransactTime() {
    return transactTime;
  }

  public void applyExecution(GatewayExecutionOutcome outcome) {
    this.status = outcome.ordStatus().name();
    this.fepOrderId = outcome.fepOrderId();
    this.execType = outcome.execType().name();
    this.executedQty = outcome.executedQty();
    this.executedPrice = outcome.executedPrice();
    this.leavesQty = outcome.leavesQty();
    this.transactTime = outcome.transactTime();
  }

  public GatewayOrderResult toResult(Instant queryTime) {
    FepOrdStatus resolvedStatus = resolveOrdStatus();
    return new GatewayOrderResult(
        clOrdId,
        fepOrderId,
        resolveExecType(resolvedStatus),
        resolvedStatus,
        executedQty,
        executedPrice,
        leavesQty,
        transactTime,
        queryTime,
        null
    );
  }

  public boolean isMarketOrder() {
    return "MARKET".equals(orderType);
  }

  public long totalQty() {
    return qty.longValueExact();
  }

  public long remainingQty() {
    if (leavesQty != null) {
      return Math.max(leavesQty, 0L);
    }
    long executed = executedQty == null ? 0L : executedQty;
    return Math.max(totalQty() - executed, 0L);
  }

  public boolean hasExecutedQuantity() {
    return executedQty != null && executedQty > 0;
  }

  public boolean requiresManualExecutionPrice() {
    return isMarketOrder()
        && ("UNKNOWN".equals(status) || "PENDING".equals(status) || "MALFORMED".equals(status));
  }

  public boolean isReplayEscalated() {
    return "ESCALATED".equals(recoveryStatus);
  }

  public boolean hasRequeryOutcome() {
    return requeryOrdStatus != null && !requeryOrdStatus.isBlank();
  }

  public Long referencePrice() {
    return requestedPrice != null ? requestedPrice : executedPrice;
  }

  public void updateRecoveryStatus(String recoveryStatus) {
    this.recoveryStatus = recoveryStatus;
  }

  public void updateCancelFailureMode(String cancelFailureMode) {
    this.cancelFailureMode = cancelFailureMode;
  }

  public void configureRequeryOutcome(String requeryOrdStatus, Long requeryExecutedQty, Long requeryExecutedPrice) {
    this.requeryOrdStatus = requeryOrdStatus;
    this.requeryExecutedQty = requeryExecutedQty;
    this.requeryExecutedPrice = requeryExecutedPrice;
  }

  public void clearRequeryOutcome() {
    this.requeryOrdStatus = null;
    this.requeryExecutedQty = null;
    this.requeryExecutedPrice = null;
  }

  public void updateRequestedPrice(Long requestedPrice) {
    this.requestedPrice = requestedPrice;
  }

  public void seedRequestedPriceIfMissing(Long requestedPrice) {
    if (this.requestedPrice == null && requestedPrice != null && requestedPrice > 0) {
      this.requestedPrice = requestedPrice;
    }
  }

  private FepExecType resolveExecType(FepOrdStatus resolvedStatus) {
    if (execType != null && !execType.isBlank()) {
      try {
        return FepExecType.valueOf(execType.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        // Fall through to legacy/default mapping.
      }
    }

    return switch (resolvedStatus) {
      case FILLED -> FepExecType.FILL;
      case PARTIALLY_FILLED -> FepExecType.PARTIAL_FILL;
      case REJECTED -> FepExecType.REJECTED;
      case CANCELED -> FepExecType.CANCELED;
      case PENDING, UNKNOWN, MALFORMED -> FepExecType.PENDING_NEW;
    };
  }

  private FepOrdStatus resolveOrdStatus() {
    if (status == null || status.isBlank()) {
      return FepOrdStatus.UNKNOWN;
    }

    try {
      return FepOrdStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return FepOrdStatus.UNKNOWN;
    }
  }
}
