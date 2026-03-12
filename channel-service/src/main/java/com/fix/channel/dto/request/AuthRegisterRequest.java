package com.fix.channel.dto.request;

import com.fix.channel.vo.AuthRegisterCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequest(
    @Email
    @NotBlank
    String email,

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "password must include uppercase, number, special char and be at least 8 chars"
    )
    String password,

    @NotBlank
    @Size(min = 2, max = 100)
    String name
) {

  public AuthRegisterCommand toVo() {
    return AuthRegisterCommand.of(email, password, name);
  }
}
