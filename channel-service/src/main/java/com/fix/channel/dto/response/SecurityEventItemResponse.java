package com.fix.channel.dto.response;

import com.fix.channel.vo.SecurityEventItemVo;

public class SecurityEventItemResponse {

  private final Long eventId;
  private final String eventType;
  private final String severity;
  private final String ipAddress;

  private SecurityEventItemResponse(Long eventId, String eventType, String severity, String ipAddress) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.severity = severity;
    this.ipAddress = ipAddress;
  }

  public static SecurityEventItemResponse from(SecurityEventItemVo itemVo) {
    return new SecurityEventItemResponse(
        itemVo.getEventId(),
        itemVo.getEventType(),
        itemVo.getSeverity(),
        itemVo.getIpAddress()
    );
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
