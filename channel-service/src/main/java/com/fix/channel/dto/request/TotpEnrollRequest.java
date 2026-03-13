package com.fix.channel.dto.request;

import com.fix.channel.vo.TotpEnrollCommand;
import jakarta.validation.constraints.NotBlank;

public record TotpEnrollRequest(
    @NotBlank
    String loginToken
) {

  public TotpEnrollCommand toVo() {
    return TotpEnrollCommand.of(loginToken);
  }
}
