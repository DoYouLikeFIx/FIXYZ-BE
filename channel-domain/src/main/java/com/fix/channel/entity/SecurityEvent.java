package com.fix.channel.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "security_events")
public class SecurityEvent extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_id")
  private Long memberId;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Column(name = "severity", nullable = false, length = 32)
  private String severity;

  protected SecurityEvent() {
  }

  private SecurityEvent(Long memberId, String eventType, String ipAddress, String userAgent, String severity) {
    this.memberId = memberId;
    this.eventType = eventType;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.severity = severity;
  }

  public static SecurityEvent of(Long memberId, String eventType, String ipAddress, String userAgent, String severity) {
    return new SecurityEvent(memberId, eventType, ipAddress, userAgent, severity);
  }

  public Long getId() {
    return id;
  }

  public Long getMemberId() {
    return memberId;
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

  public String getSeverity() {
    return severity;
  }
}
