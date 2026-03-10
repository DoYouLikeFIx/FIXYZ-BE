package com.fix.channel.vo;

import java.math.BigDecimal;

public class OrderExecuteResult {

  private final Long orderId;
  private final String clOrdId;
  private final String status;
  private final boolean idempotent;
  private final BigDecimal orderQuantity;

  private OrderExecuteResult(
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

  public static OrderExecuteResult of(
      Long orderId,
      String clOrdId,
      String status,
      boolean idempotent,
      BigDecimal orderQuantity
  ) {
    return new OrderExecuteResult(orderId, clOrdId, status, idempotent, orderQuantity);
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
