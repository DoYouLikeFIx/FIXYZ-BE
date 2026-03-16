package com.fix.channel.entity;

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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_sessions")
public class OrderSession extends BaseTimeEntity {

  public static final String ESCALATED_MANUAL_REVIEW = "ESCALATED_MANUAL_REVIEW";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_session_id", nullable = false, unique = true, length = 36, columnDefinition = "CHAR(36)")
  private String orderSessionId;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "cl_ord_id", nullable = false, unique = true, length = 64)
  private String clOrdId;

  // Legacy order_ref column now stores a deterministic replay fingerprint.
  @Column(name = "order_ref", nullable = false, length = 64)
  private String replayFingerprint;

  @Column(name = "symbol", length = 16)
  private String symbol;

  @Column(name = "side", length = 16)
  private String side;

  @Column(name = "order_type", length = 16)
  private String orderType;

  @Column(name = "qty", precision = 19, scale = 4)
  private BigDecimal qty;

  @Column(name = "price", precision = 19, scale = 4)
  private BigDecimal price;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private OrderSessionStatus status;

  @Column(name = "challenge_required", nullable = false)
  private boolean challengeRequired;

  @Column(name = "authorization_reason", nullable = false, length = 64)
  private String authorizationReason;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "execution_result", length = 32)
  private String executionResult;

  @Column(name = "executed_qty", precision = 19, scale = 4)
  private BigDecimal executedQty;

  @Column(name = "leaves_qty", precision = 19, scale = 4)
  private BigDecimal leavesQty;

  @Column(name = "executed_price", precision = 19, scale = 4)
  private BigDecimal executedPrice;

  @Column(name = "external_order_id", length = 64)
  private String externalOrderId;

  @Column(name = "failure_reason", length = 64)
  private String failureReason;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "canceled_at")
  private Instant canceledAt;

  protected OrderSession() {
  }

  private OrderSession(
      Long memberId,
      Long accountId,
      String clOrdId,
      String replayFingerprint,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      OrderSessionStatus status,
      boolean challengeRequired,
      String authorizationReason,
      Instant expiresAt
  ) {
    this.orderSessionId = UUID.randomUUID().toString();
    this.memberId = memberId;
    this.accountId = accountId;
    this.clOrdId = clOrdId;
    this.replayFingerprint = replayFingerprint;
    this.symbol = symbol;
    this.side = side;
    this.orderType = orderType;
    this.qty = qty;
    this.price = price;
    this.status = status;
    this.challengeRequired = challengeRequired;
    this.authorizationReason = authorizationReason;
    this.expiresAt = expiresAt;
  }

  public static OrderSession initiated(
      Long memberId,
      Long accountId,
      String clOrdId,
      String replayFingerprint,
      String symbol,
      String side,
      String orderType,
      BigDecimal qty,
      BigDecimal price,
      boolean challengeRequired,
      String authorizationReason,
      Instant expiresAt
  ) {
    return new OrderSession(
        memberId,
        accountId,
        clOrdId,
        replayFingerprint,
        symbol,
        side,
        orderType,
        qty,
        price,
        challengeRequired ? OrderSessionStatus.PENDING_NEW : OrderSessionStatus.AUTHED,
        challengeRequired,
        authorizationReason,
        expiresAt
    );
  }

  public Long getId() {
    return id;
  }

  public String getOrderSessionId() {
    return orderSessionId;
  }

  public Long getMemberId() {
    return memberId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getReplayFingerprint() {
    return replayFingerprint;
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

  public OrderSessionStatus getStatus() {
    return status;
  }

  public boolean isChallengeRequired() {
    return challengeRequired;
  }

  public String getAuthorizationReason() {
    return authorizationReason;
  }

  public Instant getExpiresAt() {
    return expiresAt;
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

  public boolean ownedBy(Long memberId) {
    return Objects.equals(this.memberId, memberId);
  }

  public boolean matchesReplayFingerprint(String candidateFingerprint) {
    return Objects.equals(this.replayFingerprint, candidateFingerprint);
  }

  public boolean hasActiveWindow() {
    return status != null && status.isActiveWindow();
  }

  public boolean isExpired() {
    return status == OrderSessionStatus.EXPIRED;
  }

  public void assertAwaitingOtpVerification() {
    if (this.status == OrderSessionStatus.AUTHED) {
      throw invalidTransition("order session is already authorized");
    }
    if (this.status != OrderSessionStatus.PENDING_NEW) {
      throw invalidTransition("order session is not awaiting otp verification");
    }
    if (!this.challengeRequired) {
      throw invalidTransition("order session does not require otp verification");
    }
  }

  public void authorize() {
    assertAwaitingOtpVerification();
    transitionTo(OrderSessionStatus.AUTHED, "order session is not awaiting otp verification");
    this.status = OrderSessionStatus.AUTHED;
  }

  public void extendExpiry(Instant expiresAt) {
    if (!hasActiveWindow()) {
      throw invalidTransition("order session cannot be extended in current state");
    }
    this.expiresAt = expiresAt;
  }

  public void startExecuting() {
    transitionTo(OrderSessionStatus.EXECUTING, "order session is not authorized for execution");
    this.status = OrderSessionStatus.EXECUTING;
  }

  public void complete(
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      Instant executedAt
  ) {
    transitionTo(OrderSessionStatus.COMPLETED, transitionMessage(OrderSessionStatus.COMPLETED));
    this.status = OrderSessionStatus.COMPLETED;
    this.executionResult = executionResult;
    this.executedQty = executedQty;
    this.leavesQty = leavesQty;
    this.executedPrice = executedPrice;
    this.externalOrderId = externalOrderId;
    this.executedAt = executedAt;
    this.canceledAt = null;
    this.failureReason = null;
  }

  public void escalate(String failureReason) {
    transitionTo(OrderSessionStatus.ESCALATED, transitionMessage(OrderSessionStatus.ESCALATED));
    this.status = OrderSessionStatus.ESCALATED;
    clearExecutionOutcome();
    this.failureReason = requireFailureReason(failureReason);
  }

  public void fail(String failureReason) {
    transitionTo(OrderSessionStatus.FAILED, transitionMessage(OrderSessionStatus.FAILED));
    this.status = OrderSessionStatus.FAILED;
    clearExecutionOutcome();
    this.failureReason = requireFailureReason(failureReason);
  }

  public void expire() {
    transitionTo(OrderSessionStatus.EXPIRED, transitionMessage(OrderSessionStatus.EXPIRED));
    this.status = OrderSessionStatus.EXPIRED;
    this.failureReason = null;
    this.canceledAt = null;
  }

  private void transitionTo(OrderSessionStatus nextStatus, String message) {
    if (!this.status.canTransitionTo(nextStatus)) {
      throw invalidTransition(message);
    }
  }

  private void clearExecutionOutcome() {
    this.executionResult = null;
    this.executedQty = null;
    this.leavesQty = null;
    this.executedPrice = null;
    this.externalOrderId = null;
    this.executedAt = null;
    this.canceledAt = null;
  }

  private String requireFailureReason(String failureReason) {
    if (failureReason == null || failureReason.isBlank()) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "failureReason is required");
    }
    return failureReason;
  }

  private BusinessException invalidTransition(String message) {
    return new BusinessException(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED, message);
  }

  private String transitionMessage(OrderSessionStatus nextStatus) {
    return "order session transition " + this.status + " -> " + nextStatus + " is not allowed";
  }
}
