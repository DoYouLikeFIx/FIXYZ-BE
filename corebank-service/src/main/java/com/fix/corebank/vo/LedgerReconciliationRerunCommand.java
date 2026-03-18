package com.fix.corebank.vo;

public class LedgerReconciliationRerunCommand {

  private final Long caseId;
  private final String reason;
  private final String actor;
  private final String context;
  private final String correlationId;

  private LedgerReconciliationRerunCommand(
      Long caseId,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    this.caseId = caseId;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
    this.correlationId = correlationId;
  }

  public static LedgerReconciliationRerunCommand of(
      Long caseId,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    return new LedgerReconciliationRerunCommand(caseId, reason, actor, context, correlationId);
  }

  public Long getCaseId() {
    return caseId;
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
