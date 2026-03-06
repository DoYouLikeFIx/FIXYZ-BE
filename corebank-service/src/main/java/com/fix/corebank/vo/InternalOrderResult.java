package com.fix.corebank.vo;

import java.math.BigDecimal;

public class InternalOrderResult {

  private final Long orderId;
  private final String clOrdId;
  private final String status;
  private final boolean idempotent;
  private final BigDecimal orderQuantity;

  private InternalOrderResult(
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

  public static InternalOrderResult of(
      Long orderId,
      String clOrdId,
      String status,
      boolean idempotent,
      BigDecimal orderQuantity
  ) {
    return new InternalOrderResult(orderId, clOrdId, status, idempotent, orderQuantity);
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
