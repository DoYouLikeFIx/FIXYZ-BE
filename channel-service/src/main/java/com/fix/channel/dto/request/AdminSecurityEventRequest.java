package com.fix.channel.dto.request;

import com.fix.channel.vo.AdminSecurityEventCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdminSecurityEventRequest(
    @NotNull
    Long memberId,

    @Min(1)
    @Max(100)
    Integer limit,

    Long cursorId
) {

  public AdminSecurityEventRequest {
    limit = limit == null ? 20 : limit;
  }

  public AdminSecurityEventCommand toVo() {
    return AdminSecurityEventCommand.of(memberId, limit, cursorId);
  }
}
