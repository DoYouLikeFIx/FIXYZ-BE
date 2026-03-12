package com.fix.channel.dto.response;

import com.fix.channel.vo.NotificationStreamResult;
import java.util.List;

public record NotificationStreamResponse(List<NotificationItemResponse> items) {

  public static NotificationStreamResponse from(NotificationStreamResult result) {
    List<NotificationItemResponse> items = result.getItems().stream()
        .map(NotificationItemResponse::from)
        .toList();
    return new NotificationStreamResponse(items);
  }
}
