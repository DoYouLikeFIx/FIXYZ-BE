package com.fix.channel.dto.request;

import com.fix.channel.vo.AccountPositionQueryCommand;
import jakarta.validation.constraints.NotBlank;

public record AccountPositionQueryRequest(
    @NotBlank
    String symbol
) {

  public AccountPositionQueryCommand toVo(Long accountId, Long memberId) {
    return AccountPositionQueryCommand.of(accountId, memberId, symbol);
  }
}
