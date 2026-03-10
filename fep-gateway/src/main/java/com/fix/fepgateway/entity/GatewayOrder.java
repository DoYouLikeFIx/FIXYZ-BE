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

  private static final String LEGACY_ACCOUNT_ID = "LEGACY";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cl_ord_id", nullable = false, unique = true, length = 64)
  private String clOrdId;

  @Column(name = "account_id", nullable = false, length = 64)
  private String accountId;

  @Column(name = "reference_id", nullable = false, unique = true, length = 128)
  private String referenceId;

  @Column(name = "reference_id_expires_at", nullable = false)
  private Instant referenceIdExpiresAt;

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

  @Column(name = "status_message", length = 255)
  private String message;

  @Column(name = "reject_reason", length = 64)
  private String rejectReason;

  @Column(name = "parse_error", length = 255)
  private String parseError;

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
      String accountId,
      String referenceId,
      Instant referenceIdExpiresAt,
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
      String message,
      String rejectReason,
      String parseError,
      String transport,
      String recoveryStatus,
      String cancelFailureMode,
      String requeryOrdStatus,
      Long requeryExecutedQty,
      Long requeryExecutedPrice
  ) {
    this.clOrdId = clOrdId;
    this.accountId = accountId;
    this.referenceId = referenceId;
    this.referenceIdExpiresAt = referenceIdExpiresAt;
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
    this.message = message;
    this.rejectReason = rejectReason;
    this.parseError = parseError;
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
    return received(
        clOrdId,
        LEGACY_ACCOUNT_ID,
        legacyReferenceId(clOrdId),
        Instant.EPOCH,
        symbol,
        side,
        qty,
        orderType,
        requestedPrice,
        transport
    );
  }

  public static GatewayOrder received(
      String clOrdId,
      String accountId,
      String referenceId,
      Instant referenceIdExpiresAt,
      String symbol,
      String side,
      BigDecimal qty,
      String orderType,
      Long requestedPrice,
      String transport
  ) {
    return new GatewayOrder(
        clOrdId,
        accountId,
        referenceId,
        referenceIdExpiresAt,
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
        null,
        null,
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

  public String getAccountId() {
    return accountId;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public Instant getReferenceIdExpiresAt() {
    return referenceIdExpiresAt;
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

  public String getMessage() {
    return message;
  }

  public String getRejectReason() {
    return rejectReason;
  }

  public String getParseError() {
    return parseError;
  }

  public void applyExecution(GatewayExecutionOutcome outcome) {
    this.status = outcome.ordStatus().name();
    this.fepOrderId = outcome.fepOrderId();
    this.execType = outcome.execType() != null ? outcome.execType().name() : null;
    this.executedQty = outcome.executedQty();
    this.executedPrice = outcome.executedPrice();
    this.leavesQty = outcome.leavesQty();
    this.transactTime = outcome.transactTime();
    this.message = outcome.message();
    this.rejectReason = outcome.rejectReason();
    this.parseError = outcome.parseError();
  }

  public GatewayOrderResult toResult(Instant queryTime) {
    FepOrdStatus resolvedStatus = resolveOrdStatus();
    long normalizedExecutedQty = executedQty == null ? 0L : executedQty;
    boolean hasExecutionData = normalizedExecutedQty > 0;
    boolean hasPendingExecutionDetails = hasExecutionData
        || executedPrice != null
        || transactTime != null
        || (fepOrderId != null && !fepOrderId.isBlank());
    boolean hasPendingRemainingQty = leavesQty != null && leavesQty < totalQty();

    return switch (resolvedStatus) {
      case UNKNOWN -> new GatewayOrderResult(
          clOrdId,
          null,
          null,
          resolvedStatus,
          null,
          null,
          null,
          null,
          queryTime,
          defaultIfBlank(message, "execution state is unresolved in external system"),
          null,
          null,
          null
      );
      case PENDING -> new GatewayOrderResult(
          clOrdId,
          hasPendingExecutionDetails ? fepOrderId : null,
          hasExecutionData ? resolveExecType(resolvedStatus) : null,
          resolvedStatus,
          hasExecutionData ? executedQty : null,
          hasExecutionData ? executedPrice : null,
          hasPendingExecutionDetails || hasPendingRemainingQty ? leavesQty : null,
          hasPendingExecutionDetails ? transactTime : null,
          queryTime,
          defaultIfBlank(message, "execution report is still pending"),
          null,
          null,
          null
      );
      case MALFORMED -> new GatewayOrderResult(
          clOrdId,
          hasExecutionData ? fepOrderId : null,
          hasExecutionData ? resolveExecType(resolvedStatus) : null,
          resolvedStatus,
          hasExecutionData ? executedQty : null,
          hasExecutionData ? executedPrice : null,
          hasExecutionData ? leavesQty : null,
          hasExecutionData ? transactTime : null,
          queryTime,
          defaultIfBlank(message, "FIX ExecutionReport parse failed; manual review required"),
          null,
          null,
          defaultIfBlank(parseError, "PARSE_ERROR:LEGACY_STATUS_ROW")
      );
      case REJECTED -> new GatewayOrderResult(
          clOrdId,
          null,
          FepExecType.REJECTED,
          resolvedStatus,
          null,
          null,
          null,
          transactTime,
          queryTime,
          null,
          defaultIfBlank(rejectReason, "OTHER"),
          null,
          null
      );
      case CANCELED -> new GatewayOrderResult(
          clOrdId,
          hasExecutionData ? fepOrderId : null,
          FepExecType.CANCELED,
          resolvedStatus,
          hasExecutionData ? executedQty : null,
          hasExecutionData ? executedPrice : null,
          null,
          transactTime,
          queryTime,
          null,
          null,
          Math.max(totalQty() - normalizedExecutedQty, 0L),
          null
      );
      case FILLED, PARTIALLY_FILLED -> new GatewayOrderResult(
          clOrdId,
          fepOrderId,
          resolveExecType(resolvedStatus),
          resolvedStatus,
          executedQty,
          executedPrice,
          leavesQty,
          transactTime,
          queryTime,
          null,
          null,
          null,
          null
      );
    };
  }

  public boolean isMarketOrder() {
    return "MARKET".equals(orderType);
  }

  public boolean isOwnedBy(String accountId) {
    return this.accountId != null && this.accountId.equals(accountId);
  }

  public boolean usesReferenceId(String referenceId) {
    return this.referenceId != null && this.referenceId.equals(referenceId);
  }

  public boolean isReferenceIdExpired(Instant now) {
    return referenceIdExpiresAt != null && referenceIdExpiresAt.isBefore(now);
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

  private String defaultIfBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
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

  private static String legacyReferenceId(String clOrdId) {
    return "LEGACY-" + clOrdId;
  }
}
