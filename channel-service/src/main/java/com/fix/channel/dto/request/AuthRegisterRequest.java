package com.fix.channel.dto.request;

import com.fix.channel.vo.AuthRegisterCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthRegisterRequest {

  @Email
  @NotBlank
  private String email;

  @NotBlank
  @Pattern(
      regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
      message = "password must include uppercase, number, special char and be at least 8 chars"
  )
  private String password;

  @NotBlank
  @Size(min = 2, max = 100)
  private String name;

  public AuthRegisterCommand toVo() {
    return AuthRegisterCommand.of(email, password, name);
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
