package com.fix.channel.vo;

public class TotpEnrollCommand {

  private final String loginToken;

  private TotpEnrollCommand(String loginToken) {
    this.loginToken = loginToken;
  }

  public static TotpEnrollCommand of(String loginToken) {
    return new TotpEnrollCommand(loginToken);
  }

  public String getLoginToken() {
    return loginToken;
  }
}
