package com.fix.channel.dto.request;

import com.fix.channel.vo.MemberProfileUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MemberProfileUpdateRequest {

  @NotBlank
  @Size(min = 2, max = 100)
  private String name;

  public MemberProfileUpdateCommand toVo() {
    return MemberProfileUpdateCommand.of(name);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
