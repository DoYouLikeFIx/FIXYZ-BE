package com.fix.channel.vo;

public class AdminAccountStatusTransitionCommand {

  private final Long accountId;
  private final Long memberId;
  private final String status;
  private final String reason;
  private final String actor;
  private final String context;

  private AdminAccountStatusTransitionCommand(
      Long accountId,
      Long memberId,
      String status,
      String reason,
      String actor,
      String context
  ) {
    this.accountId = accountId;
    this.memberId = memberId;
    this.status = status;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
  }

  public static AdminAccountStatusTransitionCommand of(
      Long accountId,
      Long memberId,
      String status,
      String reason,
      String actor,
      String context
  ) {
    return new AdminAccountStatusTransitionCommand(accountId, memberId, status, reason, actor, context);
  }

  public Long getAccountId() {
    return accountId;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getStatus() {
    return status;
  }

  public String getReason() {
    return reason;
  }

  public String getActor() {
    return actor;
  }

  public String getContext() {
    return context;
  }
}
