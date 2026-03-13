package com.fix.channel.dto.request;

import com.fix.channel.vo.OrderSessionOtpVerifyCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OrderSessionOtpVerifyRequest(
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "otpCode must be 6 digits")
    String otpCode
) {

  public OrderSessionOtpVerifyCommand toVo(Long memberId, String orderSessionId) {
    return OrderSessionOtpVerifyCommand.of(memberId, orderSessionId, otpCode);
  }
}
