package com.fix.channel.dto.response;

import com.fix.channel.vo.AdminMonitoringFreshnessResult;
import java.time.Instant;
import java.util.List;

public record AdminMonitoringFreshnessResponse(List<Item> items) {

  public static AdminMonitoringFreshnessResponse from(AdminMonitoringFreshnessResult result) {
    return new AdminMonitoringFreshnessResponse(
        result.items().stream()
            .map(item -> new Item(
                item.key(),
                item.status(),
                item.statusMessage(),
                item.lastUpdatedAt()
            ))
            .toList()
    );
  }

  public record Item(
      String key,
      String status,
      String statusMessage,
      Instant lastUpdatedAt
  ) {
  }
}
