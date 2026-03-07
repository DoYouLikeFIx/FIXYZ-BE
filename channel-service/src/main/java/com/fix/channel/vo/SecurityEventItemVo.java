package com.fix.channel.vo;

public class SecurityEventItemVo {

  private final Long eventId;
  private final String eventType;
  private final String severity;
  private final String ipAddress;

  private SecurityEventItemVo(Long eventId, String eventType, String severity, String ipAddress) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.severity = severity;
    this.ipAddress = ipAddress;
  }

  public static SecurityEventItemVo of(Long eventId, String eventType, String severity, String ipAddress) {
    return new SecurityEventItemVo(eventId, eventType, severity, ipAddress);
  }

  public Long getEventId() {
    return eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getSeverity() {
    return severity;
  }

  public String getIpAddress() {
    return ipAddress;
  }
}
