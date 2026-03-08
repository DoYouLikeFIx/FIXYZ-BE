package com.fix.channel.vo;

public class AuthRegisterCommand {

  private final String email;
  private final String password;
  private final String name;

  private AuthRegisterCommand(String email, String password, String name) {
    this.email = email;
    this.password = password;
    this.name = name;
  }

  public static AuthRegisterCommand of(String email, String password, String name) {
    return new AuthRegisterCommand(email, password, name);
  }

  public String getEmail() {
    return email;
  }

  public String getPassword() {
    return password;
  }

  public String getName() {
    return name;
  }
}
