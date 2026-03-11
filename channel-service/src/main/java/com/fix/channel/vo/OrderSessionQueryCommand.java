package com.fix.channel.vo;

public class OrderSessionQueryCommand {

  private final Long memberId;
  private final String orderSessionId;
  private final String clOrdId;

  private OrderSessionQueryCommand(Long memberId, String orderSessionId, String clOrdId) {
    this.memberId = memberId;
    this.orderSessionId = orderSessionId;
    this.clOrdId = clOrdId;
  }

  public static OrderSessionQueryCommand of(Long memberId, String orderSessionId, String clOrdId) {
    return new OrderSessionQueryCommand(memberId, orderSessionId, clOrdId);
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getOrderSessionId() {
    return orderSessionId;
  }

  public String getClOrdId() {
    return clOrdId;
  }
}
