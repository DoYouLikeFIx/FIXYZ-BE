package com.fix.channel.vo;

public class NotificationStreamCommand {

  private final Long memberId;
  private final Integer limit;
  private final Long cursorId;

  private NotificationStreamCommand(Long memberId, Integer limit, Long cursorId) {
    this.memberId = memberId;
    this.limit = limit;
    this.cursorId = cursorId;
  }

  public static NotificationStreamCommand of(Long memberId, Integer limit, Long cursorId) {
    return new NotificationStreamCommand(memberId, limit, cursorId);
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
