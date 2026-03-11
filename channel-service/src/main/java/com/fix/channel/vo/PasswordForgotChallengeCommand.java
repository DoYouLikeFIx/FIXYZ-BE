package com.fix.channel.vo;

public class PasswordForgotChallengeCommand {

  private final String email;

  private PasswordForgotChallengeCommand(String email) {
    this.email = email;
  }

  public static PasswordForgotChallengeCommand of(String email) {
    return new PasswordForgotChallengeCommand(email);
  }

  public String getEmail() {
    return email;
  }
}
