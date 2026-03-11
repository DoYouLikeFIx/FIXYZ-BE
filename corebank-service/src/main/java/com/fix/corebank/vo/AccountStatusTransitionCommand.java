package com.fix.corebank.vo;

public class AccountStatusTransitionCommand {

  private final Long accountId;
  private final Long memberId;
  private final String targetStatus;
  private final String reason;
  private final String actor;
  private final String context;
  private final String correlationId;

  private AccountStatusTransitionCommand(
      Long accountId,
      Long memberId,
      String targetStatus,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    this.accountId = accountId;
    this.memberId = memberId;
    this.targetStatus = targetStatus;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
    this.correlationId = correlationId;
  }

  public static AccountStatusTransitionCommand of(
      Long accountId,
      Long memberId,
      String targetStatus,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    return new AccountStatusTransitionCommand(accountId, memberId, targetStatus, reason, actor, context, correlationId);
  }

  public Long getAccountId() {
    return accountId;
  }

  public Long getMemberId() {
    return memberId;
  }

  public String getTargetStatus() {
    return targetStatus;
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

  public String getCorrelationId() {
    return correlationId;
  }
}
