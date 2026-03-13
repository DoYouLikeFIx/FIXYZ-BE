package com.fix.channel.vo;

public class MfaRecoveryRebindConfirmResult {

  private final boolean rebindCompleted;
  private final boolean reauthRequired;

  private MfaRecoveryRebindConfirmResult(boolean rebindCompleted, boolean reauthRequired) {
    this.rebindCompleted = rebindCompleted;
    this.reauthRequired = reauthRequired;
  }

  public static MfaRecoveryRebindConfirmResult completed() {
    return new MfaRecoveryRebindConfirmResult(true, true);
  }

  public boolean isRebindCompleted() {
    return rebindCompleted;
  }

  public boolean isReauthRequired() {
    return reauthRequired;
  }
}
