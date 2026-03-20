package com.fix.channel.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fix.channel.vo.PasswordForgotCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordForgotRequest(
    @Email
    @NotBlank
    String email,

    String challengeToken,

    String challengeAnswer,

    JsonNode challengeAnswerPayload
) {

  public PasswordForgotCommand toVo() {
    return PasswordForgotCommand.of(
        email,
        challengeToken,
        challengeAnswer,
        challengeAnswerPayload != null
    );
  }
}
