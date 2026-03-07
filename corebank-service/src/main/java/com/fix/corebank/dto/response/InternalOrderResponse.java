package com.fix.corebank.dto.response;

import com.fix.corebank.vo.InternalOrderResult;
import java.math.BigDecimal;

public class InternalOrderResponse {

  private final Long orderId;
  private final String clOrdId;
  private final String status;
  private final boolean idempotent;
  private final BigDecimal orderQuantity;

  private InternalOrderResponse(
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

  public static InternalOrderResponse from(InternalOrderResult result) {
    return new InternalOrderResponse(
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
