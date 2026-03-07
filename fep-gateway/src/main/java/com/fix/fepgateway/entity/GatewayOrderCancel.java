package com.fix.fepgateway.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "gateway_order_cancels")
public class GatewayOrderCancel extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cl_ord_id", nullable = false, length = 64)
  private String clOrdId;

  @Column(name = "reason", nullable = false, length = 255)
  private String reason;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  protected GatewayOrderCancel() {
  }

  private GatewayOrderCancel(String clOrdId, String reason, String status) {
    this.clOrdId = clOrdId;
    this.reason = reason;
    this.status = status;
  }

  public static GatewayOrderCancel requested(String clOrdId, String reason) {
    return new GatewayOrderCancel(clOrdId, reason, "CANCEL_REQUESTED");
  }

  public Long getId() {
    return id;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getReason() {
    return reason;
  }

  public String getStatus() {
    return status;
  }
}
