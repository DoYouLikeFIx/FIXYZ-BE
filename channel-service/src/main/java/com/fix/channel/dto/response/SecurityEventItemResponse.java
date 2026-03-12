package com.fix.channel.dto.response;

import com.fix.channel.vo.SecurityEventItemVo;

public record SecurityEventItemResponse(
    Long eventId,
    String eventType,
    String severity,
    String ipAddress
) {

  public static SecurityEventItemResponse from(SecurityEventItemVo itemVo) {
    return new SecurityEventItemResponse(
        itemVo.getEventId(),
        itemVo.getEventType(),
        itemVo.getSeverity(),
        itemVo.getIpAddress()
    );
  }
}
