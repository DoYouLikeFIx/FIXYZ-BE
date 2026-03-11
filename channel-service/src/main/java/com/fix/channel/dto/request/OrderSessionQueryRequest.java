package com.fix.channel.dto.request;

import com.fix.channel.vo.OrderSessionQueryCommand;
import com.fix.common.validation.ContractPatterns;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

public record OrderSessionQueryRequest(
    @Parameter(description = "Server-issued order session id. Provide exactly one of orderSessionId or clOrdId.")
    @Pattern(regexp = ContractPatterns.UUID_V4)
    String orderSessionId,

    @Parameter(description = "Client order id. Provide exactly one of orderSessionId or clOrdId.")
    @Pattern(regexp = ContractPatterns.UUID_V4)
    String clOrdId
) {

  @Schema(hidden = true)
  @AssertTrue(message = "exactly one of orderSessionId or clOrdId is required")
  public boolean hasExactlyOneLookupTarget() {
    return hasText(orderSessionId) ^ hasText(clOrdId);
  }

  public OrderSessionQueryCommand toVo(Long memberId) {
    return OrderSessionQueryCommand.of(memberId, orderSessionId, clOrdId);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
