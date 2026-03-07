package com.fix.channel.vo;

public class OrderSessionCreateCommand {

  private final Long memberId;
  private final String clOrdId;
  private final String orderRef;

  private OrderSessionCreateCommand(Long memberId, String clOrdId, String orderRef) {
    this.memberId = memberId;
    this.clOrdId = clOrdId;
    this.orderRef = orderRef;
  }

  public static OrderSessionCreateCommand of(Long memberId, String clOrdId, String orderRef) {
    return new OrderSessionCreateCommand(memberId, clOrdId, orderRef);
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public String getOrderRef() {
    return orderRef;
  }
}
