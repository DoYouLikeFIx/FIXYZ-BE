package com.fix.channel.vo;

public class OrderSessionResult {

  private final Long sessionId;
  private final String clOrdId;
  private final String status;

  private OrderSessionResult(Long sessionId, String clOrdId, String status) {
    this.sessionId = sessionId;
    this.clOrdId = clOrdId;
    this.status = status;
  }

  public static OrderSessionResult of(Long sessionId, String clOrdId, String status) {
    return new OrderSessionResult(sessionId, clOrdId, status);
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
