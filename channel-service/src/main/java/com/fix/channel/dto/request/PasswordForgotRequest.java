package com.fix.channel.dto.request;

import com.fix.channel.vo.PasswordForgotCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordForgotRequest(
    @Email
    @NotBlank
    String email,

    String challengeToken,

    String challengeAnswer
) {

  public PasswordForgotCommand toVo() {
    return PasswordForgotCommand.of(email, challengeToken, challengeAnswer);
  }
}
