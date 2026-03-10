package com.fix.channel.dto.request;

import com.fix.channel.vo.PasswordForgotChallengeCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class PasswordForgotChallengeRequest {

  @Email
  @NotBlank
  private String email;

  public PasswordForgotChallengeCommand toVo() {
    return PasswordForgotChallengeCommand.of(email);
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}
