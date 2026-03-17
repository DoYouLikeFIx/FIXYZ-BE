package com.fix.channel.vo;

import java.time.Instant;

public class NotificationItemVo {

  private final Long notificationId;
  private final String channel;
  private final String message;
  private final boolean delivered;
  private final Instant readAt;

  private NotificationItemVo(Long notificationId, String channel, String message, boolean delivered, Instant readAt) {
    this.notificationId = notificationId;
    this.channel = channel;
    this.message = message;
    this.delivered = delivered;
    this.readAt = readAt;
  }

  public static NotificationItemVo of(Long notificationId, String channel, String message, boolean delivered, Instant readAt) {
    return new NotificationItemVo(notificationId, channel, message, delivered, readAt);
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

  public Instant getReadAt() {
    return readAt;
  }

  public boolean isRead() {
    return readAt != null;
  }
}
