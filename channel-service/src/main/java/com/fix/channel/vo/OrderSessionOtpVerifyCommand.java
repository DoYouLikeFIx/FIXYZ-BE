package com.fix.channel.vo;

public class OrderSessionOtpVerifyCommand {

  private final Long memberId;
  private final String orderSessionId;
  private final String otpCode;

  private OrderSessionOtpVerifyCommand(Long memberId, String orderSessionId, String otpCode) {
    this.memberId = memberId;
    this.orderSessionId = orderSessionId;
    this.otpCode = otpCode;
  }

  public static OrderSessionOtpVerifyCommand of(Long memberId, String orderSessionId, String otpCode) {
    return new OrderSessionOtpVerifyCommand(memberId, orderSessionId, otpCode);
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getOrderSessionId() {
    return orderSessionId;
  }

  public String getOtpCode() {
    return otpCode;
  }
}
