package com.fix.channel.vo;

public class OtpVerifyResult {

  private final boolean verified;

  private OtpVerifyResult(boolean verified) {
    this.verified = verified;
  }

  public static OtpVerifyResult of(boolean verified) {
    return new OtpVerifyResult(verified);
  }

  public boolean isVerified() {
    return verified;
  }
}
