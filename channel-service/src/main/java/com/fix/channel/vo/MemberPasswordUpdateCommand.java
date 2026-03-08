package com.fix.channel.vo;

public class MemberPasswordUpdateCommand {

  private final String currentPassword;
  private final String newPassword;

  private MemberPasswordUpdateCommand(String currentPassword, String newPassword) {
    this.currentPassword = currentPassword;
    this.newPassword = newPassword;
  }

  public static MemberPasswordUpdateCommand of(String currentPassword, String newPassword) {
    return new MemberPasswordUpdateCommand(currentPassword, newPassword);
  }

  public String getCurrentPassword() {
    return currentPassword;
  }

  public String getNewPassword() {
    return newPassword;
  }
}
