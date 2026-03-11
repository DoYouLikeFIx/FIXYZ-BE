package com.fix.channel.vo;

public class PasswordForgotResult {

  private final boolean accepted;
  private final String message;
  private final String challengeEndpoint;
  private final boolean challengeMayBeRequired;

  private PasswordForgotResult(
      boolean accepted,
      String message,
      String challengeEndpoint,
      boolean challengeMayBeRequired
  ) {
    this.accepted = accepted;
    this.message = message;
    this.challengeEndpoint = challengeEndpoint;
    this.challengeMayBeRequired = challengeMayBeRequired;
  }

  public static PasswordForgotResult accepted(
      String message,
      String challengeEndpoint,
      boolean challengeMayBeRequired
  ) {
    return new PasswordForgotResult(true, message, challengeEndpoint, challengeMayBeRequired);
  }

  public boolean isAccepted() {
    return accepted;
  }

  public String getMessage() {
    return message;
  }

  public String getChallengeEndpoint() {
    return challengeEndpoint;
  }

  public boolean isChallengeMayBeRequired() {
    return challengeMayBeRequired;
  }
}
