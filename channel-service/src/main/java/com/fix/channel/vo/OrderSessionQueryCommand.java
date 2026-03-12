package com.fix.channel.vo;

public class OrderSessionQueryCommand {

  private final Long memberId;
  private final String orderSessionId;

  private OrderSessionQueryCommand(Long memberId, String orderSessionId) {
    this.memberId = memberId;
    this.orderSessionId = orderSessionId;
  }

  public static OrderSessionQueryCommand of(Long memberId, String orderSessionId) {
    return new OrderSessionQueryCommand(memberId, orderSessionId);
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getOrderSessionId() {
    return orderSessionId;
  }
}
