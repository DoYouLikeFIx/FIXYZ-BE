package com.fix.channel.vo;

public class TotpConfirmCommand {

  private final String loginToken;
  private final String enrollmentToken;
  private final String otpCode;

  private TotpConfirmCommand(String loginToken, String enrollmentToken, String otpCode) {
    this.loginToken = loginToken;
    this.enrollmentToken = enrollmentToken;
    this.otpCode = otpCode;
  }

  public static TotpConfirmCommand of(String loginToken, String enrollmentToken, String otpCode) {
    return new TotpConfirmCommand(loginToken, enrollmentToken, otpCode);
  }

  public String getLoginToken() {
    return loginToken;
  }

  public String getEnrollmentToken() {
    return enrollmentToken;
  }

  public String getOtpCode() {
    return otpCode;
  }
}
