package com.fix.channel.dto.request;

import com.fix.channel.vo.AuthLoginCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
    @Email
    @NotBlank
    String email,

    @NotBlank
    String password
) {

  public AuthLoginCommand toVo() {
    return AuthLoginCommand.of(email, password);
  }
}
