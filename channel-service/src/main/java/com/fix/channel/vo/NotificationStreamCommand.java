package com.fix.channel.vo;

public class NotificationStreamCommand {

  private final Long memberId;
  private final Integer limit;

  private NotificationStreamCommand(Long memberId, Integer limit) {
    this.memberId = memberId;
    this.limit = limit;
  }

  public static NotificationStreamCommand of(Long memberId, Integer limit) {
    return new NotificationStreamCommand(memberId, limit);
  }

  public Long getMemberId() {
    return memberId;
  }

  public Integer getLimit() {
    return limit;
  }
}
