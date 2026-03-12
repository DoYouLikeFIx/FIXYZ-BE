package com.fix.channel.vo;

public class OtpVerifyCommand {

  private final String loginToken;
  private final String otpCode;

  private OtpVerifyCommand(String loginToken, String otpCode) {
    this.loginToken = loginToken;
    this.otpCode = otpCode;
  }

  public static OtpVerifyCommand of(String loginToken, String otpCode) {
    return new OtpVerifyCommand(loginToken, otpCode);
  }

  public String getLoginToken() {
    return loginToken;
  }

  public String getOtpCode() {
    return otpCode;
  }
}
