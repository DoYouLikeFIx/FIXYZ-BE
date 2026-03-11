package com.fix.channel.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.channel.vo.OrderSessionResult;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderSessionResponse(
    String orderSessionId,
    String clOrdId,
    String status,
    Instant expiresAt,
    Long remainingSeconds
) {

  public static OrderSessionResponse from(OrderSessionResult result) {
    return new OrderSessionResponse(
        result.getOrderSessionId(),
        result.getClOrdId(),
        result.getStatus(),
        result.getExpiresAt(),
        result.getRemainingSeconds()
    );
  }
}
