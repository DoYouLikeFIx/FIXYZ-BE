package com.fix.fepgateway.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "gateway_security_events")
public class GatewaySecurityEvent extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(name = "severity", nullable = false, length = 16)
  private String severity;

  @Column(name = "reference_id", nullable = false, length = 128)
  private String referenceId;

  @Column(name = "owner_account_id", length = 64)
  private String ownerAccountId;

  @Column(name = "attempted_account_id", length = 64)
  private String attemptedAccountId;

  @Column(name = "owner_cl_ord_id", length = 64)
  private String ownerClOrdId;

  @Column(name = "attempted_cl_ord_id", length = 64)
  private String attemptedClOrdId;

  @Column(name = "correlation_id", nullable = false, length = 64)
  private String correlationId;

  @Column(name = "detail", nullable = false, length = 255)
  private String detail;

  protected GatewaySecurityEvent() {
  }

  private GatewaySecurityEvent(
      String eventType,
      String severity,
      String referenceId,
      String ownerAccountId,
      String attemptedAccountId,
      String ownerClOrdId,
      String attemptedClOrdId,
      String correlationId,
      String detail
  ) {
    this.eventType = eventType;
    this.severity = severity;
    this.referenceId = referenceId;
    this.ownerAccountId = ownerAccountId;
    this.attemptedAccountId = attemptedAccountId;
    this.ownerClOrdId = ownerClOrdId;
    this.attemptedClOrdId = attemptedClOrdId;
    this.correlationId = correlationId;
    this.detail = detail;
  }

  public static GatewaySecurityEvent deniedReplay(
      String eventType,
      String referenceId,
      String ownerAccountId,
      String attemptedAccountId,
      String ownerClOrdId,
      String attemptedClOrdId,
      String correlationId,
      String detail
  ) {
    return new GatewaySecurityEvent(
        eventType,
        "WARN",
        referenceId,
        ownerAccountId,
        attemptedAccountId,
        ownerClOrdId,
        attemptedClOrdId,
        correlationId,
        detail
    );
  }
}
