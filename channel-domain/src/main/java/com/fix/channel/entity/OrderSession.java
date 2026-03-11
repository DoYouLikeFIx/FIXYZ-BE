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

  protected OrderSession() {
  }

  private OrderSession(Long memberId, String clOrdId, String orderRef, OrderSessionStatus status) {
    this.orderSessionId = UUID.randomUUID().toString();
    this.memberId = memberId;
    this.clOrdId = clOrdId;
    this.orderRef = orderRef;
    this.status = status;
  }

  public static OrderSession pendingNew(Long memberId, String clOrdId, String orderRef) {
    return new OrderSession(memberId, clOrdId, orderRef, OrderSessionStatus.PENDING_NEW);
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

  public boolean ownedBy(Long memberId) {
    return Objects.equals(this.memberId, memberId);
  }

  public void expire() {
    this.status = OrderSessionStatus.EXPIRED;
  }
}
