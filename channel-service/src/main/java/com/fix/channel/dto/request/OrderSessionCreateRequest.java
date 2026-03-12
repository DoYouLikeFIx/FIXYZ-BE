package com.fix.channel.dto.request;

import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.common.validation.ContractPatterns;
import java.time.Instant;
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

  public OrderSessionCreateCommand toVo(
      Long memberId,
      Instant lastMfaVerifiedAt,
      Instant loginAuthenticatedAt,
      boolean challengeBypassEligible,
      String loginIpAddress,
      String loginUserAgent,
      String clientIpAddress,
      String clientUserAgent
  ) {
    return OrderSessionCreateCommand.of(
        memberId,
        clOrdId,
        orderRef,
        lastMfaVerifiedAt,
        loginAuthenticatedAt,
        challengeBypassEligible,
        loginIpAddress,
        loginUserAgent,
        clientIpAddress,
        clientUserAgent
    );
  }
}
