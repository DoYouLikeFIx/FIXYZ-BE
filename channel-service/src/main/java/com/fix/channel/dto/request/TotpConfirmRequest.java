package com.fix.channel.dto.request;

import com.fix.channel.vo.TotpConfirmCommand;
import jakarta.validation.constraints.NotBlank;

public record TotpConfirmRequest(
    @NotBlank
    String loginToken,

    @NotBlank
    String enrollmentToken,

    @NotBlank
    String otpCode
) {

  public TotpConfirmCommand toVo() {
    return TotpConfirmCommand.of(loginToken, enrollmentToken, otpCode);
  }
}
