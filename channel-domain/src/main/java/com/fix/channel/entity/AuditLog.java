package com.fix.channel.entity;

import com.fix.common.entity.BaseTimeEntity;
import com.fix.common.web.CorrelationIdSupport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "audit_uuid", length = 36, unique = true, columnDefinition = "CHAR(36)")
  private String auditUuid;

  @Column(name = "member_id")
  private Long memberId;

  @Column(name = "order_session_id")
  private Long orderSessionId;

  @Column(name = "action", nullable = false, length = 64)
  private String action;

  @Column(name = "target_type", nullable = false, length = 64)
  private String targetType;

  @Column(name = "target_id", length = 100)
  private String targetId;

  @Column(name = "detail", length = 1000)
  private String detail;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 1000)
  private String userAgent;

  @Column(name = "correlation_uuid", length = 36, columnDefinition = "CHAR(36)")
  private String correlationUuid;

  protected AuditLog() {
  }

  private AuditLog(
      Long memberId,
      Long orderSessionId,
      String action,
      String targetType,
      String targetId,
      String detail,
      String ipAddress,
      String userAgent,
      String correlationUuid
  ) {
    this.memberId = memberId;
    this.orderSessionId = orderSessionId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.detail = detail;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.correlationUuid = normalizeCorrelationId(correlationUuid);
  }

  public static AuditLog of(Long memberId, String action, String targetType, String targetId, String detail) {
    return new AuditLog(memberId, null, action, targetType, targetId, detail, null, null, null);
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
    return new AuditLog(memberId, null, action, targetType, targetId, detail, ipAddress, userAgent, correlationId);
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

  public static AuditLog ofOrderSession(
      Long memberId,
      Long orderSessionId,
      String action,
      String targetType,
      String targetId,
      String detail,
      String ipAddress,
      String userAgent,
      String correlationId
  ) {
    return new AuditLog(
        memberId,
        orderSessionId,
        action,
        targetType,
        targetId,
        detail,
        ipAddress,
        userAgent,
        correlationId
    );
  }

  public static AuditLog ofOrderSession(
      Long memberId,
      Long orderSessionId,
      AuditAction action,
      String targetType,
      String targetId,
      String detail,
      String ipAddress,
      String userAgent,
      String correlationId
  ) {
    return ofOrderSession(memberId, orderSessionId, action.value(), targetType, targetId, detail, ipAddress, userAgent,
        correlationId);
  }

  @PrePersist
  protected void onCreate() {
    super.onCreate();
    correlationUuid = normalizeCorrelationId(correlationUuid);
    if (auditUuid == null || auditUuid.isBlank()) {
      auditUuid = UUID.randomUUID().toString();
    }
  }

  public AuditLog withOrderSessionId(Long orderSessionId) {
    this.orderSessionId = orderSessionId;
    return this;
  }

  public Long getId() {
    return id;
  }

  public String getAuditUuid() {
    return auditUuid;
  }

  public Long getMemberId() {
    return memberId;
  }

  public Long getOrderSessionId() {
    return orderSessionId;
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
    return correlationUuid;
  }

  public String getCorrelationUuid() {
    return correlationUuid;
  }

  private static String normalizeCorrelationId(String correlationId) {
    return CorrelationIdSupport.normalize(correlationId, 36);
  }
}
