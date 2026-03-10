package com.fix.channel.dto.response;

import com.fix.channel.vo.PasswordForgotResult;

public class PasswordForgotResponse {

  private final boolean accepted;
  private final String message;
  private final RecoveryResponse recovery;

  private PasswordForgotResponse(boolean accepted, String message, RecoveryResponse recovery) {
    this.accepted = accepted;
    this.message = message;
    this.recovery = recovery;
  }

  public static PasswordForgotResponse from(PasswordForgotResult result) {
    return new PasswordForgotResponse(
        result.isAccepted(),
        result.getMessage(),
        new RecoveryResponse(result.getChallengeEndpoint(), result.isChallengeMayBeRequired())
    );
  }

  public boolean isAccepted() {
    return accepted;
  }

  public String getMessage() {
    return message;
  }

  public RecoveryResponse getRecovery() {
    return recovery;
  }

  public static class RecoveryResponse {

    private final String challengeEndpoint;
    private final boolean challengeMayBeRequired;

    private RecoveryResponse(String challengeEndpoint, boolean challengeMayBeRequired) {
      this.challengeEndpoint = challengeEndpoint;
      this.challengeMayBeRequired = challengeMayBeRequired;
    }

    public String getChallengeEndpoint() {
      return challengeEndpoint;
    }

    public boolean isChallengeMayBeRequired() {
      return challengeMayBeRequired;
    }
  }
}
