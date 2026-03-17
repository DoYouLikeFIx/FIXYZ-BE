package com.fix.channel.dto.request;

import com.fix.channel.vo.NotificationStreamCommand;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record NotificationStreamRequest(
    @Min(1)
    @Max(100)
    Integer limit,

    Long cursorId
) {

  public NotificationStreamRequest {
    limit = limit == null ? 20 : limit;
  }

  public NotificationStreamCommand toVo(Long memberId) {
    return NotificationStreamCommand.of(memberId, limit, cursorId);
  }
}
