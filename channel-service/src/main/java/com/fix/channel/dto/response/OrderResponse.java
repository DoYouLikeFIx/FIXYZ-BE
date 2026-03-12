package com.fix.channel.dto.response;

import com.fix.channel.vo.OrderExecuteResult;
import java.math.BigDecimal;

public record OrderResponse(
    Long orderId,
    String clOrdId,
    String status,
    boolean idempotent,
    BigDecimal orderQuantity
) {

  public static OrderResponse from(OrderExecuteResult result) {
    return new OrderResponse(
        result.getOrderId(),
        result.getClOrdId(),
        result.getStatus(),
        result.isIdempotent(),
        result.getOrderQuantity()
    );
  }
}
