package com.fix.channel.vo;

public class OrderSessionQueryCommand {

  private final Long sessionId;
  private final String clOrdId;

  private OrderSessionQueryCommand(Long sessionId, String clOrdId) {
    this.sessionId = sessionId;
    this.clOrdId = clOrdId;
  }

  public static OrderSessionQueryCommand of(Long sessionId, String clOrdId) {
    return new OrderSessionQueryCommand(sessionId, clOrdId);
  }

  public Long getSessionId() {
    return sessionId;
  }

  public String getClOrdId() {
    return clOrdId;
  }
}
