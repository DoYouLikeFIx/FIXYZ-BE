package com.fix.channel.dto.response;

import com.fix.channel.vo.PasswordForgotResult;

public record PasswordForgotResponse(
    boolean accepted,
    String message,
    RecoveryResponse recovery
) {

  public static PasswordForgotResponse from(PasswordForgotResult result) {
    return new PasswordForgotResponse(
        result.isAccepted(),
        result.getMessage(),
        new RecoveryResponse(result.getChallengeEndpoint(), result.isChallengeMayBeRequired())
    );
  }

  public record RecoveryResponse(String challengeEndpoint, boolean challengeMayBeRequired) {
  }
}
