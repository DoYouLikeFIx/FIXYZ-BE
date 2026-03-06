package com.fix.channel.vo;

public class AuthLoginCommand {

  private final String memberNo;
  private final String password;

  private AuthLoginCommand(String memberNo, String password) {
    this.memberNo = memberNo;
    this.password = password;
  }

  public static AuthLoginCommand of(String memberNo, String password) {
    return new AuthLoginCommand(memberNo, password);
  }

  public String getMemberNo() {
    return memberNo;
  }

  public String getPassword() {
    return password;
  }
}
