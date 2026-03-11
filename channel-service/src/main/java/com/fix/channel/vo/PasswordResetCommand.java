package com.fix.channel.vo;

public class PasswordResetCommand {

  private final String token;
  private final String newPassword;

  private PasswordResetCommand(String token, String newPassword) {
    this.token = token;
    this.newPassword = newPassword;
  }

  public static PasswordResetCommand of(String token, String newPassword) {
    return new PasswordResetCommand(token, newPassword);
  }

  public String getToken() {
    return token;
  }

  public String getNewPassword() {
    return newPassword;
  }
}
