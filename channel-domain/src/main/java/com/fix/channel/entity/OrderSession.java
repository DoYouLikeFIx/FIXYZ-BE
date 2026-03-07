package com.fix.channel.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_sessions")
public class OrderSession extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id", nullable = false)
  private Long memberId;

  @Column(name = "cl_ord_id", nullable = false, unique = true, length = 64)
  private String clOrdId;

  @Column(name = "order_ref", nullable = false, length = 64)
  private String orderRef;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  protected OrderSession() {
  }

  private OrderSession(Long memberId, String clOrdId, String orderRef, String status) {
    this.memberId = memberId;
    this.clOrdId = clOrdId;
    this.orderRef = orderRef;
    this.status = status;
  }

  public static OrderSession open(Long memberId, String clOrdId, String orderRef) {
    return new OrderSession(memberId, clOrdId, orderRef, "OPEN");
  }

  public Long getId() {
    return id;
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

  public String getStatus() {
    return status;
  }

  public void close() {
    this.status = "CLOSED";
  }
}
