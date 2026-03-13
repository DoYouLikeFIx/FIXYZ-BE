package com.fix.channel.dto.request;

import com.fix.channel.vo.MemberTotpRebindCommand;
import jakarta.validation.constraints.NotBlank;

public record MemberTotpRebindRequest(
    @NotBlank
    String currentPassword
) {

  public MemberTotpRebindCommand toVo() {
    return MemberTotpRebindCommand.of(currentPassword);
  }
}
