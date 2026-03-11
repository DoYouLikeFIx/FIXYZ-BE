package com.fix.channel.vo;

public class PasswordForgotCommand {

  private final String email;
  private final String challengeToken;
  private final String challengeAnswer;

  private PasswordForgotCommand(String email, String challengeToken, String challengeAnswer) {
    this.email = email;
    this.challengeToken = challengeToken;
    this.challengeAnswer = challengeAnswer;
  }

  public static PasswordForgotCommand of(String email, String challengeToken, String challengeAnswer) {
    return new PasswordForgotCommand(email, challengeToken, challengeAnswer);
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
}
