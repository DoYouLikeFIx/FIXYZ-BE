package com.fix.channel.vo;

public class PasswordForgotCommand {

  private final String email;
  private final String challengeToken;
  private final String challengeAnswer;
  private final boolean challengeAnswerPayloadPresent;

  private PasswordForgotCommand(
      String email,
      String challengeToken,
      String challengeAnswer,
      boolean challengeAnswerPayloadPresent
  ) {
    this.email = email;
    this.challengeToken = challengeToken;
    this.challengeAnswer = challengeAnswer;
    this.challengeAnswerPayloadPresent = challengeAnswerPayloadPresent;
  }

  public static PasswordForgotCommand of(
      String email,
      String challengeToken,
      String challengeAnswer,
      boolean challengeAnswerPayloadPresent
  ) {
    return new PasswordForgotCommand(email, challengeToken, challengeAnswer, challengeAnswerPayloadPresent);
  }

  public String getEmail() {
    return email;
  }

  public String getChallengeToken() {
    return challengeToken;
  }

  public String getChallengeAnswer() {
    return challengeAnswer;
  }

  public boolean isChallengeAnswerPayloadPresent() {
    return challengeAnswerPayloadPresent;
  }
}
