package com.fix.channel.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id")
  private Long memberId;

  @Column(name = "action", nullable = false, length = 64)
  private String action;

  @Column(name = "target_type", nullable = false, length = 64)
  private String targetType;

  @Column(name = "target_id", length = 64)
  private String targetId;

  @Column(name = "detail", length = 1000)
  private String detail;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Column(name = "correlation_id", length = 128)
  private String correlationId;

  protected AuditLog() {
  }

  private AuditLog(
      Long memberId,
      String action,
      String targetType,
      String targetId,
      String detail,
      String ipAddress,
      String userAgent,
      String correlationId
  ) {
    this.memberId = memberId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.detail = detail;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.correlationId = correlationId;
  }

  public static AuditLog of(Long memberId, String action, String targetType, String targetId, String detail) {
    return new AuditLog(memberId, action, targetType, targetId, detail, null, null, null);
  }

  public static AuditLog of(
      Long memberId,
      AuditAction action,
      String targetType,
      String targetId,
      String detail
  ) {
    return of(memberId, action.value(), targetType, targetId, detail);
  }

  public static AuditLog of(
      Long memberId,
      String action,
      String targetType,
      String targetId,
      String detail,
      String ipAddress,
      String userAgent,
      String correlationId
  ) {
    return new AuditLog(memberId, action, targetType, targetId, detail, ipAddress, userAgent, correlationId);
  }

  public static AuditLog of(
      Long memberId,
      AuditAction action,
      String targetType,
      String targetId,
      String detail,
      String ipAddress,
      String userAgent,
      String correlationId
  ) {
    return of(memberId, action.value(), targetType, targetId, detail, ipAddress, userAgent, correlationId);
  }

  public Long getId() {
    return id;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getAction() {
    return action;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public String getDetail() {
    return detail;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getCorrelationId() {
    return correlationId;
  }
}
