package com.fix.channel.dto.request;

import com.fix.channel.vo.OtpVerifyCommand;
import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
    @NotBlank
    String loginToken,

    @NotBlank
    String otpCode
) {

  public OtpVerifyCommand toVo() {
    return OtpVerifyCommand.of(loginToken, otpCode);
  }
}
