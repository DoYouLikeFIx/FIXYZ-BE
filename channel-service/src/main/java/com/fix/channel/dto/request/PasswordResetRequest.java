package com.fix.channel.dto.request;

import com.fix.channel.vo.PasswordResetCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetRequest(
    @NotBlank
    String token,

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "password must include uppercase, number, special char and be at least 8 chars"
    )
    String newPassword
) {

  public PasswordResetCommand toVo() {
    return PasswordResetCommand.of(token, newPassword);
  }
}
