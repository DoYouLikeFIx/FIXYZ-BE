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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_events")
public class SecurityEvent extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "security_event_uuid", length = 36, unique = true, columnDefinition = "CHAR(36)")
  private String securityEventUuid;

  @Column(name = "member_id")
  private Long memberId;

  @Column(name = "admin_member_id")
  private Long adminMemberId;

  @Column(name = "order_session_id")
  private Long orderSessionId;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(name = "status", nullable = false, length = 32)
  private String status;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Column(name = "severity", nullable = false, length = 32)
  private String severity;

  @Column(name = "detail", length = 2000)
  private String detail;

  @Column(name = "correlation_uuid", length = 36, columnDefinition = "CHAR(36)")
  private String correlationUuid;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  protected SecurityEvent() {
  }

  private SecurityEvent(
      Long memberId,
      Long adminMemberId,
      Long orderSessionId,
      String eventType,
      String ipAddress,
      String userAgent,
      String severity,
      String detail,
      String correlationUuid,
      Instant occurredAt,
      String status,
      Instant resolvedAt
  ) {
    this.memberId = memberId;
    this.adminMemberId = adminMemberId;
    this.orderSessionId = orderSessionId;
    this.eventType = eventType;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.severity = severity;
    this.detail = detail;
    this.correlationUuid = normalizeCorrelationId(correlationUuid);
    this.occurredAt = occurredAt;
    this.status = status;
    this.resolvedAt = resolvedAt;
  }

  public static SecurityEvent of(Long memberId, String eventType, String ipAddress, String userAgent, String severity) {
    return new SecurityEvent(
        memberId,
        null,
        null,
        eventType,
        ipAddress,
        userAgent,
        severity,
        null,
        null,
        null,
        SecurityEventStatus.OPEN.name(),
        null
    );
  }

  @PrePersist
  protected void applySecurityDefaultsOnPersist() {
    correlationUuid = normalizeCorrelationId(correlationUuid);
    if (securityEventUuid == null || securityEventUuid.isBlank()) {
      securityEventUuid = UUID.randomUUID().toString();
    }
    if (status == null || status.isBlank()) {
      status = SecurityEventStatus.OPEN.name();
    }
    if (occurredAt == null) {
      occurredAt = Instant.now();
    }
  }

  public SecurityEvent withAdminMemberId(Long adminMemberId) {
    this.adminMemberId = adminMemberId;
    return this;
  }

  public SecurityEvent withOrderSessionId(Long orderSessionId) {
    this.orderSessionId = orderSessionId;
    return this;
  }

  public SecurityEvent withDetail(String detail) {
    this.detail = detail;
    return this;
  }

  public SecurityEvent withCorrelationId(String correlationUuid) {
    this.correlationUuid = normalizeCorrelationId(correlationUuid);
    return this;
  }

  public SecurityEvent withOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
    return this;
  }

  public SecurityEvent acknowledge(Long adminMemberId) {
    this.adminMemberId = adminMemberId;
    this.status = SecurityEventStatus.ACKNOWLEDGED.name();
    return this;
  }

  public SecurityEvent resolve(Long adminMemberId, Instant resolvedAt) {
    this.adminMemberId = adminMemberId;
    this.status = SecurityEventStatus.RESOLVED.name();
    this.resolvedAt = resolvedAt;
    return this;
  }

  public Long getId() {
    return id;
  }

  public String getSecurityEventUuid() {
    return securityEventUuid;
  }

  public Long getMemberId() {
    return memberId;
  }

  public Long getAdminMemberId() {
    return adminMemberId;
  }

  public Long getOrderSessionId() {
    return orderSessionId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getStatus() {
    return status;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getSeverity() {
    return severity;
  }

  public String getDetail() {
    return detail;
  }

  public String getCorrelationId() {
    return correlationUuid;
  }

  public String getCorrelationUuid() {
    return correlationUuid;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  private static String normalizeCorrelationId(String correlationId) {
    return CorrelationIdSupport.normalize(correlationId, 36);
  }
}
