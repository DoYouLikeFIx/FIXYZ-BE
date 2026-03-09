package com.fix.fepgateway.entity;

import com.fix.common.entity.BaseTimeEntity;
import com.fix.common.fep.FepCancelStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "gateway_order_cancels")
public class GatewayOrderCancel extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cl_ord_id", nullable = false, length = 64)
  private String origClOrdId;

  @Column(name = "cancel_cl_ord_id", unique = true, length = 64)
  private String cancelClOrdId;

  @Column(name = "reason", nullable = false, length = 255)
  private String reason;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "canceled_qty")
  private Long canceledQty;

  @Column(name = "executed_qty")
  private Long executedQty;

  @Column(name = "executed_price")
  private Long executedPrice;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "canceled_at")
  private Instant canceledAt;

  protected GatewayOrderCancel() {
  }

  private GatewayOrderCancel(String origClOrdId, String cancelClOrdId, String reason, String status) {
    this.origClOrdId = origClOrdId;
    this.cancelClOrdId = cancelClOrdId;
    this.reason = reason;
    this.status = status;
  }

  public static GatewayOrderCancel requested(String origClOrdId, String cancelClOrdId, String reason) {
    return new GatewayOrderCancel(origClOrdId, cancelClOrdId, reason, "CANCEL_REQUESTED");
  }

  public void complete(
      FepCancelStatus status,
      Long canceledQty,
      Long executedQty,
      Long executedPrice,
      Instant executedAt,
      Instant canceledAt
  ) {
    this.status = status.name();
    this.canceledQty = canceledQty;
    this.executedQty = executedQty;
    this.executedPrice = executedPrice;
    this.executedAt = executedAt;
    this.canceledAt = canceledAt;
  }

  public Long getId() {
    return id;
  }

  public String getOrigClOrdId() {
    return origClOrdId;
  }

  public String getCancelClOrdId() {
    return cancelClOrdId;
  }

  public String getReason() {
    return reason;
  }

  public String getStatus() {
    return status;
  }

  public Long getCanceledQty() {
    return canceledQty;
  }

  public Long getExecutedQty() {
    return executedQty;
  }

  public Long getExecutedPrice() {
    return executedPrice;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public Instant getCanceledAt() {
    return canceledAt;
  }
}
