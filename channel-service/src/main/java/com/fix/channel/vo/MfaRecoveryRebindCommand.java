package com.fix.channel.vo;

public class MfaRecoveryRebindCommand {

  private final String recoveryProof;

  private MfaRecoveryRebindCommand(String recoveryProof) {
    this.recoveryProof = recoveryProof;
  }

  public static MfaRecoveryRebindCommand of(String recoveryProof) {
    return new MfaRecoveryRebindCommand(recoveryProof);
  }

  public String getRecoveryProof() {
    return recoveryProof;
  }
}
