package com.fix.channel.dto.request;

import com.fix.channel.vo.AccountOrderHistoryQueryCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AccountOrderHistoryQueryRequest(
    @Min(0)
    Integer page,

    @Min(1)
    @Max(100)
    Integer size
) {

  public AccountOrderHistoryQueryRequest {
    page = page == null ? 0 : page;
    size = size == null ? 20 : size;
  }

  public AccountOrderHistoryQueryCommand toVo(Long accountId, Long memberId) {
    return AccountOrderHistoryQueryCommand.of(accountId, memberId, page, size);
  }
}
