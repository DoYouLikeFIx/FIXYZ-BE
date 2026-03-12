package com.fix.channel.dto.request;

import com.fix.channel.vo.PasswordForgotChallengeCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordForgotChallengeRequest(
    @Email
    @NotBlank
    String email
) {

  public PasswordForgotChallengeCommand toVo() {
    return PasswordForgotChallengeCommand.of(email);
  }
}
