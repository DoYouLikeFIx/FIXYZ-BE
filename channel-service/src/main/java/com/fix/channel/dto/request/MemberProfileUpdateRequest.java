package com.fix.channel.dto.request;

import com.fix.channel.vo.MemberProfileUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberProfileUpdateRequest(
    @NotBlank
    @Size(min = 2, max = 100)
    String name
) {

  public MemberProfileUpdateRequest {
    name = name == null ? null : name.trim();
  }

  public MemberProfileUpdateCommand toVo() {
    return MemberProfileUpdateCommand.of(name);
  }
}
