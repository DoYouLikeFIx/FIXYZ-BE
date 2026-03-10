package com.fix.channel.dto.response;

import com.fix.channel.vo.OrderExecuteResult;
import java.math.BigDecimal;

public class OrderResponse {

  private final Long orderId;
  private final String clOrdId;
  private final String status;
  private final boolean idempotent;
  private final BigDecimal orderQuantity;

  private OrderResponse(
      Long orderId,
      String clOrdId,
      String status,
      boolean idempotent,
      BigDecimal orderQuantity
  ) {
    this.orderId = orderId;
    this.clOrdId = clOrdId;
    this.status = status;
    this.idempotent = idempotent;
    this.orderQuantity = orderQuantity;
  }

  public static OrderResponse from(OrderExecuteResult result) {
    return new OrderResponse(
        result.getOrderId(),
        result.getClOrdId(),
        result.getStatus(),
        result.isIdempotent(),
        result.getOrderQuantity()
    );
  }

  public Long getOrderId() {
    return orderId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getStatus() {
    return status;
  }

  public boolean isIdempotent() {
    return idempotent;
  }

  public BigDecimal getOrderQuantity() {
    return orderQuantity;
  }
}
