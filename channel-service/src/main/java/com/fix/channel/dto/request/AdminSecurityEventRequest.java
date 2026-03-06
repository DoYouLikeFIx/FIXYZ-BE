package com.fix.channel.dto.request;

import com.fix.channel.vo.AdminSecurityEventCommand;
import jakarta.validation.constraints.NotNull;

public class AdminSecurityEventRequest {

  @NotNull
  private Long memberId;

  private Integer limit = 20;

  public AdminSecurityEventCommand toVo() {
    return AdminSecurityEventCommand.of(memberId, limit);
  }

  public Long getMemberId() {
    return memberId;
  }

  public void setMemberId(Long memberId) {
    this.memberId = memberId;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }
}
