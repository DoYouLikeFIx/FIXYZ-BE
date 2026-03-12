package com.fix.channel.dto.response;

import com.fix.channel.vo.OtpVerifyResult;

public record OtpVerifyResponse(boolean verified) {

  public static OtpVerifyResponse from(OtpVerifyResult result) {
    return new OtpVerifyResponse(result.isVerified());
  }
}
