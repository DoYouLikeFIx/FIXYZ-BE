package com.fix.channel.dto.request;

import com.fix.channel.vo.NotificationStreamCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class NotificationStreamRequest {

  @NotNull
  private Long memberId;

  @Min(1)
  @Max(100)
  private Integer limit = 20;

  private Long cursorId;

  public NotificationStreamCommand toVo() {
    return NotificationStreamCommand.of(memberId, limit, cursorId);
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

  public Long getCursorId() {
    return cursorId;
  }

  public void setCursorId(Long cursorId) {
    this.cursorId = cursorId;
  }
}
