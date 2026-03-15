package com.fix.channel.vo;

public record OrderSessionOtpVerifyCommand(
    Long memberId,
    String orderSessionId,
    String otpCode
) {

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
