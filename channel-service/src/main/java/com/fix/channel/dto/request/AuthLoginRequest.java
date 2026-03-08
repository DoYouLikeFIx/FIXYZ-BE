package com.fix.channel.dto.request;

import com.fix.channel.vo.AuthLoginCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthLoginRequest {

  @Email
  @NotBlank
  private String email;

  @NotBlank
  private String password;

  public AuthLoginCommand toVo() {
    return AuthLoginCommand.of(email, password);
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
