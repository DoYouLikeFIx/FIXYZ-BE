package com.fix.channel.dto.request;

import com.fix.channel.vo.MfaRecoveryRebindConfirmCommand;
import jakarta.validation.constraints.NotBlank;

public record MfaRecoveryRebindConfirmRequest(
    @NotBlank
    String rebindToken,

    @NotBlank
    String enrollmentToken,

    @NotBlank
    String otpCode
) {

  public MfaRecoveryRebindConfirmCommand toVo() {
    return MfaRecoveryRebindConfirmCommand.of(rebindToken, enrollmentToken, otpCode);
  }
}
