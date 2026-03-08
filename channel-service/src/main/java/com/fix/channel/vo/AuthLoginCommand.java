package com.fix.channel.vo;

public class AuthLoginCommand {

  private final String email;
  private final String password;

  private AuthLoginCommand(String email, String password) {
    this.email = email;
    this.password = password;
  }

  public static AuthLoginCommand of(String email, String password) {
    return new AuthLoginCommand(email, password);
  }

  public String getEmail() {
    return email;
  }

  public String getPassword() {
    return password;
  }
}
