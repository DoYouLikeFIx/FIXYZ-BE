package com.fix.channel.dto.response;

import com.fix.channel.vo.NotificationStreamResult;
import java.util.List;

public class NotificationStreamResponse {

  private final List<NotificationItemResponse> items;

  private NotificationStreamResponse(List<NotificationItemResponse> items) {
    this.items = items;
  }

  public static NotificationStreamResponse from(NotificationStreamResult result) {
    List<NotificationItemResponse> items = result.getItems().stream()
        .map(NotificationItemResponse::from)
        .toList();
    return new NotificationStreamResponse(items);
  }

  public List<NotificationItemResponse> getItems() {
    return items;
  }
}
