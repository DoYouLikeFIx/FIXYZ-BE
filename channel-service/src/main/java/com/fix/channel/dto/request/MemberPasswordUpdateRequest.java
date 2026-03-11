package com.fix.channel.dto.request;

import com.fix.channel.vo.MemberPasswordUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MemberPasswordUpdateRequest(
    @NotBlank
    String currentPassword,

    @NotBlank
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "password must include uppercase, number, special char and be at least 8 chars"
    )
    String newPassword
) {

  public MemberPasswordUpdateCommand toVo() {
    return MemberPasswordUpdateCommand.of(currentPassword, newPassword);
  }
}
