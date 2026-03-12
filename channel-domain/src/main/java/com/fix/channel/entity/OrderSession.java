package com.fix.channel.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "order_sessions")
public class OrderSession extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_session_id", nullable = false, unique = true, length = 36, columnDefinition = "CHAR(36)")
  private String orderSessionId;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "cl_ord_id", nullable = false, unique = true, length = 64)
  private String clOrdId;

  @Column(name = "order_ref", nullable = false, length = 64)
  private String orderRef;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private OrderSessionStatus status;

  @Column(name = "challenge_required", nullable = false)
  private boolean challengeRequired;

  @Enumerated(EnumType.STRING)
  @Column(name = "authorization_reason", nullable = false, length = 64)
  private OrderSessionAuthorizationReason authorizationReason;

  protected OrderSession() {
  }

  private OrderSession(
      Long memberId,
      String clOrdId,
      String orderRef,
      OrderSessionStatus status,
      boolean challengeRequired,
      OrderSessionAuthorizationReason authorizationReason
  ) {
    this.orderSessionId = UUID.randomUUID().toString();
    this.memberId = memberId;
    this.clOrdId = clOrdId;
    this.orderRef = orderRef;
    this.status = status;
    this.challengeRequired = challengeRequired;
    this.authorizationReason = Objects.requireNonNull(authorizationReason, "authorizationReason");
  }

  public static OrderSession pendingNew(Long memberId, String clOrdId, String orderRef) {
    return pendingNew(memberId, clOrdId, orderRef, OrderSessionAuthorizationReason.STEP_UP_REQUIRED);
  }

  public static OrderSession pendingNew(
      Long memberId,
      String clOrdId,
      String orderRef,
      OrderSessionAuthorizationReason authorizationReason
  ) {
    return new OrderSession(
        memberId,
        clOrdId,
        orderRef,
        OrderSessionStatus.PENDING_NEW,
        true,
        authorizationReason
    );
  }

  public static OrderSession authed(
      Long memberId,
      String clOrdId,
      String orderRef,
      OrderSessionAuthorizationReason authorizationReason
  ) {
    return new OrderSession(
        memberId,
        clOrdId,
        orderRef,
        OrderSessionStatus.AUTHED,
        false,
        authorizationReason
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

  public String getClOrdId() {
    return clOrdId;
  }

  public String getOrderRef() {
    return orderRef;
  }

  public OrderSessionStatus getStatus() {
    return status;
  }

  public boolean isChallengeRequired() {
    return challengeRequired;
  }

  public OrderSessionAuthorizationReason getAuthorizationReason() {
    return authorizationReason;
  }

  public boolean ownedBy(Long memberId) {
    return Objects.equals(this.memberId, memberId);
  }

  public void authorize(OrderSessionAuthorizationReason authorizationReason) {
    this.status = OrderSessionStatus.AUTHED;
    this.challengeRequired = false;
    this.authorizationReason = Objects.requireNonNull(authorizationReason, "authorizationReason");
  }

  public void expire() {
    this.status = OrderSessionStatus.EXPIRED;
  }
}
