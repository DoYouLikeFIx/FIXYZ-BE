package com.fix.channel.dto.response;

import com.fix.channel.vo.AdminSecurityEventResult;
import java.util.List;

public class AdminSecurityEventResponse {

  private final List<SecurityEventItemResponse> items;

  private AdminSecurityEventResponse(List<SecurityEventItemResponse> items) {
    this.items = items;
  }

  public static AdminSecurityEventResponse from(AdminSecurityEventResult result) {
    List<SecurityEventItemResponse> items = result.getItems().stream()
        .map(SecurityEventItemResponse::from)
        .toList();
    return new AdminSecurityEventResponse(items);
  }

  public List<SecurityEventItemResponse> getItems() {
    return items;
  }
}
