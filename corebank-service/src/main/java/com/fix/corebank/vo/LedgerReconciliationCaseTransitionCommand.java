package com.fix.corebank.vo;

public class LedgerReconciliationCaseTransitionCommand {

  private final Long caseId;
  private final String targetStatus;
  private final String reason;
  private final String actor;
  private final String context;
  private final String correlationId;

  private LedgerReconciliationCaseTransitionCommand(
      Long caseId,
      String targetStatus,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    this.caseId = caseId;
    this.targetStatus = targetStatus;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
    this.correlationId = correlationId;
  }

  public static LedgerReconciliationCaseTransitionCommand of(
      Long caseId,
      String targetStatus,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    return new LedgerReconciliationCaseTransitionCommand(
        caseId,
        targetStatus,
        reason,
        actor,
        context,
        correlationId
    );
  }

  public Long getCaseId() {
    return caseId;
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
