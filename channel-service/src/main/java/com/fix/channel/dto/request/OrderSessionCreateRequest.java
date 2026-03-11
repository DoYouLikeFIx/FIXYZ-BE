package com.fix.channel.dto.request;

import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.common.validation.ContractPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderSessionCreateRequest(
    @NotBlank
    @Pattern(regexp = ContractPatterns.UUID_V4)
    String clOrdId,

    @NotBlank
    @Size(min = 1, max = 64, message = "size must be between 1 and 64")
    String orderRef
) {

  public OrderSessionCreateCommand toVo(Long memberId) {
    return OrderSessionCreateCommand.of(memberId, clOrdId, orderRef);
  }
}
