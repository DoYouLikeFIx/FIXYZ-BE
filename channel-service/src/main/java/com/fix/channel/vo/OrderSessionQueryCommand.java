package com.fix.channel.vo;

public record OrderSessionQueryCommand(
    Long memberId,
    String orderSessionId
) {

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
