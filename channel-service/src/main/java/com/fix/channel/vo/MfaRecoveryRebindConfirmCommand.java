package com.fix.channel.vo;

public class MfaRecoveryRebindConfirmCommand {

  private final String rebindToken;
  private final String enrollmentToken;
  private final String otpCode;

  private MfaRecoveryRebindConfirmCommand(String rebindToken, String enrollmentToken, String otpCode) {
    this.rebindToken = rebindToken;
    this.enrollmentToken = enrollmentToken;
    this.otpCode = otpCode;
  }

  public static MfaRecoveryRebindConfirmCommand of(String rebindToken, String enrollmentToken, String otpCode) {
    return new MfaRecoveryRebindConfirmCommand(rebindToken, enrollmentToken, otpCode);
  }

  public String getRebindToken() {
    return rebindToken;
  }

  public String getEnrollmentToken() {
    return enrollmentToken;
  }

  public String getOtpCode() {
    return otpCode;
  }
}
