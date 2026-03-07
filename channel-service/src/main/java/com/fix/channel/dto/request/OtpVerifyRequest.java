package com.fix.channel.dto.request;

import com.fix.channel.vo.OtpVerifyCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OtpVerifyRequest {

  @NotNull
  private Long memberId;

  @NotBlank
  private String otpCode;

  public OtpVerifyCommand toVo() {
    return OtpVerifyCommand.of(memberId, otpCode);
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long memberId) {
    this.memberId = memberId;
  }

  public String getOtpCode() {
    return otpCode;
  }

  public void setOtpCode(String otpCode) {
    this.otpCode = otpCode;
  }
}
