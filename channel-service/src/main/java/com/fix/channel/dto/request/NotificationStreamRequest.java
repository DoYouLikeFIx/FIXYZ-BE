package com.fix.channel.dto.request;

import com.fix.channel.vo.NotificationStreamCommand;
import jakarta.validation.constraints.NotNull;

public class NotificationStreamRequest {

  @NotNull
  private Long memberId;

  private Integer limit = 20;

  public NotificationStreamCommand toVo() {
    return NotificationStreamCommand.of(memberId, limit);
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
