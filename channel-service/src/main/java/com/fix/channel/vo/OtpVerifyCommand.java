package com.fix.channel.vo;

public class OtpVerifyCommand {

  private final Long memberId;
  private final String otpCode;

  private OtpVerifyCommand(Long memberId, String otpCode) {
    this.memberId = memberId;
    this.otpCode = otpCode;
  }

  public static OtpVerifyCommand of(Long memberId, String otpCode) {
    return new OtpVerifyCommand(memberId, otpCode);
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getOtpCode() {
    return otpCode;
  }
}
