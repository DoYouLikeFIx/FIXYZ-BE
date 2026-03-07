package com.fix.channel.dto.response;

import com.fix.channel.vo.OrderSessionResult;

public class OrderSessionResponse {

  private final Long sessionId;
  private final String clOrdId;
  private final String status;

  private OrderSessionResponse(Long sessionId, String clOrdId, String status) {
    this.sessionId = sessionId;
    this.clOrdId = clOrdId;
    this.status = status;
  }

  public static OrderSessionResponse from(OrderSessionResult result) {
    return new OrderSessionResponse(result.getSessionId(), result.getClOrdId(), result.getStatus());
  }

  public Long getSessionId() {
    return sessionId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getStatus() {
    return status;
  }
}
