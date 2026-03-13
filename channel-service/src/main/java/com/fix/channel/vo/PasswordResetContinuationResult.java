package com.fix.channel.vo;

public class PasswordResetContinuationResult {

  private final String mfaRecoveryProof;
  private final long mfaRecoveryProofExpiresInSeconds;

  private PasswordResetContinuationResult(String mfaRecoveryProof, long mfaRecoveryProofExpiresInSeconds) {
    this.mfaRecoveryProof = mfaRecoveryProof;
    this.mfaRecoveryProofExpiresInSeconds = mfaRecoveryProofExpiresInSeconds;
  }

  public static PasswordResetContinuationResult none() {
    return new PasswordResetContinuationResult(null, 0L);
  }

  public static PasswordResetContinuationResult withRecoveryProof(String mfaRecoveryProof, long expiresInSeconds) {
    return new PasswordResetContinuationResult(mfaRecoveryProof, expiresInSeconds);
  }

  public boolean hasMfaRecoveryProof() {
    return mfaRecoveryProof != null && !mfaRecoveryProof.isBlank() && mfaRecoveryProofExpiresInSeconds > 0L;
  }

  public String getMfaRecoveryProof() {
    return mfaRecoveryProof;
  }

  public long getMfaRecoveryProofExpiresInSeconds() {
    return mfaRecoveryProofExpiresInSeconds;
  }
}
