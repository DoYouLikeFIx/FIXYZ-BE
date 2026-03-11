package com.fix.channel.dto.response;

import com.fix.channel.vo.AdminSecurityEventResult;
import java.util.List;

public record AdminSecurityEventResponse(List<SecurityEventItemResponse> items) {

  public static AdminSecurityEventResponse from(AdminSecurityEventResult result) {
    List<SecurityEventItemResponse> items = result.getItems().stream()
        .map(SecurityEventItemResponse::from)
        .toList();
    return new AdminSecurityEventResponse(items);
  }
}
