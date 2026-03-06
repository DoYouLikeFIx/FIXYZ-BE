package com.fix.channel.vo;

public class AdminSecurityEventCommand {

  private final Long memberId;
  private final Integer limit;

  private AdminSecurityEventCommand(Long memberId, Integer limit) {
    this.memberId = memberId;
    this.limit = limit;
  }

  public static AdminSecurityEventCommand of(Long memberId, Integer limit) {
    return new AdminSecurityEventCommand(memberId, limit);
  }

  public Long getMemberId() {
    return memberId;
  }

  public Integer getLimit() {
    return limit;
  }
}
