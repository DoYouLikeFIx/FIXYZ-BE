package com.fix.channel.dto.request;

import com.fix.channel.vo.MfaRecoveryRebindCommand;
import jakarta.validation.constraints.NotBlank;

public record MfaRecoveryRebindRequest(
    @NotBlank
    String recoveryProof
) {

  public MfaRecoveryRebindCommand toVo() {
    return MfaRecoveryRebindCommand.of(recoveryProof);
  }
}
