package com.fix.channel.dto.request;

import com.fix.channel.vo.OtpVerifyCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OtpVerifyRequest(
    @NotNull
    Long memberId,

    @NotBlank
    String otpCode
) {

  public OtpVerifyCommand toVo() {
    return OtpVerifyCommand.of(memberId, otpCode);
  }
}
