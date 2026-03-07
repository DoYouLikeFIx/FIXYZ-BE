package com.fix.channel.dto.response;

import com.fix.channel.vo.NotificationItemVo;

public class NotificationItemResponse {

  private final Long notificationId;
  private final String channel;
  private final String message;
  private final boolean delivered;

  private NotificationItemResponse(Long notificationId, String channel, String message, boolean delivered) {
    this.notificationId = notificationId;
    this.channel = channel;
    this.message = message;
    this.delivered = delivered;
  }

  public static NotificationItemResponse from(NotificationItemVo itemVo) {
    return new NotificationItemResponse(
        itemVo.getNotificationId(),
        itemVo.getChannel(),
        itemVo.getMessage(),
        itemVo.isDelivered()
    );
  }

  public Long getNotificationId() {
    return notificationId;
  }

  public String getChannel() {
    return channel;
  }

  public String getMessage() {
    return message;
  }

  public boolean isDelivered() {
    return delivered;
  }
}
