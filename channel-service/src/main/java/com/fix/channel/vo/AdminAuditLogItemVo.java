package com.fix.channel.vo;

import java.time.Instant;

public class AdminAuditLogItemVo {

  private final String auditId;
  private final Long memberId;
  private final String memberUuid;
  private final String email;
  private final String eventType;
  private final String ipAddress;
  private final String userAgent;
  private final String description;
  private final String clOrdId;
  private final Long orderSessionId;
  private final Instant createdAt;

  private AdminAuditLogItemVo(
      String auditId,
      Long memberId,
      String memberUuid,
      String email,
      String eventType,
      String ipAddress,
      String userAgent,
      String description,
      String clOrdId,
      Long orderSessionId,
      Instant createdAt
  ) {
    this.auditId = auditId;
    this.memberId = memberId;
    this.memberUuid = memberUuid;
    this.email = email;
    this.eventType = eventType;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.description = description;
    this.clOrdId = clOrdId;
    this.orderSessionId = orderSessionId;
    this.createdAt = createdAt;
  }

  public static AdminAuditLogItemVo of(
      String auditId,
      Long memberId,
      String memberUuid,
      String email,
      String eventType,
      String ipAddress,
      String userAgent,
      String description,
      String clOrdId,
      Long orderSessionId,
      Instant createdAt
  ) {
    return new AdminAuditLogItemVo(
        auditId,
        memberId,
        memberUuid,
        email,
        eventType,
        ipAddress,
        userAgent,
        description,
        clOrdId,
        orderSessionId,
        createdAt
    );
  }

  public String getAuditId() {
    return auditId;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getMemberUuid() {
    return memberUuid;
  }

  public String getEmail() {
    return email;
  }

  public String getEventType() {
    return eventType;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getDescription() {
    return description;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public Long getOrderSessionId() {
    return orderSessionId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
