package com.fix.channel.vo;

import java.util.List;

public class NotificationStreamResult {

  private final List<NotificationItemVo> items;

  private NotificationStreamResult(List<NotificationItemVo> items) {
    this.items = items;
  }

  public static NotificationStreamResult of(List<NotificationItemVo> items) {
    return new NotificationStreamResult(items);
  }

  public List<NotificationItemVo> getItems() {
    return items;
  }
}
