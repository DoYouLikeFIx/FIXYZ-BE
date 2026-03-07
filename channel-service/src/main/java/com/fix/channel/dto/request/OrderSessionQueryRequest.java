package com.fix.channel.dto.request;

import com.fix.channel.vo.OrderSessionQueryCommand;

public class OrderSessionQueryRequest {

  private Long sessionId;
  private String clOrdId;

  public OrderSessionQueryCommand toVo() {
    return OrderSessionQueryCommand.of(sessionId, clOrdId);
  }

  public Long getSessionId() {
    return sessionId;
  }

  public void setSessionId(Long sessionId) {
    this.sessionId = sessionId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public void setClOrdId(String clOrdId) {
    this.clOrdId = clOrdId;
  }
}
