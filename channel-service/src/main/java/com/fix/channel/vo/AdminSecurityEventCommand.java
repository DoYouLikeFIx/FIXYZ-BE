package com.fix.channel.vo;

public class AdminSecurityEventCommand {

  private final Long memberId;
  private final Integer limit;
  private final Long cursorId;

  private AdminSecurityEventCommand(Long memberId, Integer limit, Long cursorId) {
    this.memberId = memberId;
    this.limit = limit;
    this.cursorId = cursorId;
  }

  public static AdminSecurityEventCommand of(Long memberId, Integer limit, Long cursorId) {
    return new AdminSecurityEventCommand(memberId, limit, cursorId);
  }

  public Long getMemberId() {
    return memberId;
  }

  public Integer getLimit() {
    return limit;
  }

  public Long getCursorId() {
    return cursorId;
  }
}
