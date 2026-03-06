package com.fix.channel.dto.response;

import com.fix.channel.vo.OtpVerifyResult;

public class OtpVerifyResponse {

  private final boolean verified;

  private OtpVerifyResponse(boolean verified) {
    this.verified = verified;
  }

  public static OtpVerifyResponse from(OtpVerifyResult result) {
    return new OtpVerifyResponse(result.isVerified());
  }

  public boolean isVerified() {
    return verified;
  }
}
