package com.fix.channel.vo;

import java.time.Instant;
import java.util.List;

public record AdminMonitoringFreshnessResult(List<Item> items) {

  public record Item(
      String key,
      String status,
      String statusMessage,
      Instant lastUpdatedAt
  ) {
  }
}
