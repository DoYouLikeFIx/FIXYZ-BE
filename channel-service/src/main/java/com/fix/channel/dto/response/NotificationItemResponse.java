package com.fix.channel.dto.response;

import java.time.Instant;

import com.fix.channel.vo.NotificationItemVo;

public record NotificationItemResponse(
    Long notificationId,
    String channel,
    String message,
  boolean delivered,
  boolean read,
  Instant readAt
) {

  public static NotificationItemResponse from(NotificationItemVo itemVo) {
    return new NotificationItemResponse(
        itemVo.getNotificationId(),
        itemVo.getChannel(),
        itemVo.getMessage(),
        itemVo.isDelivered(),
        itemVo.isRead(),
        itemVo.getReadAt()
    );
  }
}
