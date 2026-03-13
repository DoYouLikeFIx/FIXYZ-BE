package com.fix.channel.vo;

public class MemberTotpRebindCommand {

  private final String currentPassword;

  private MemberTotpRebindCommand(String currentPassword) {
    this.currentPassword = currentPassword;
  }

  public static MemberTotpRebindCommand of(String currentPassword) {
    return new MemberTotpRebindCommand(currentPassword);
  }

  public String getCurrentPassword() {
    return currentPassword;
  }
}
