package com.fix.channel.dto.response;

import com.fix.channel.vo.MfaRecoveryRebindConfirmResult;

public record MfaRecoveryRebindConfirmResponse(
    boolean rebindCompleted,
    boolean reauthRequired
) {

  public static MfaRecoveryRebindConfirmResponse from(MfaRecoveryRebindConfirmResult result) {
    return new MfaRecoveryRebindConfirmResponse(
        result.isRebindCompleted(),
        result.isReauthRequired()
    );
  }
}
