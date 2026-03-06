package com.fix.channel.vo;

public class NotificationItemVo {

  private final Long notificationId;
  private final String channel;
  private final String message;
  private final boolean delivered;

  private NotificationItemVo(Long notificationId, String channel, String message, boolean delivered) {
    this.notificationId = notificationId;
    this.channel = channel;
    this.message = message;
    this.delivered = delivered;
  }

  public static NotificationItemVo of(Long notificationId, String channel, String message, boolean delivered) {
    return new NotificationItemVo(notificationId, channel, message, delivered);
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
