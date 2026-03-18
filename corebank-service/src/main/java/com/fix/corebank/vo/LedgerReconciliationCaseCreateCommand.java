package com.fix.corebank.vo;

public class LedgerReconciliationCaseCreateCommand {

  private final Long anomalyId;
  private final String reason;
  private final String actor;
  private final String context;
  private final String correlationId;

  private LedgerReconciliationCaseCreateCommand(
      Long anomalyId,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    this.anomalyId = anomalyId;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
    this.correlationId = correlationId;
  }

  public static LedgerReconciliationCaseCreateCommand of(
      Long anomalyId,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    return new LedgerReconciliationCaseCreateCommand(anomalyId, reason, actor, context, correlationId);
  }

  public Long getAnomalyId() {
    return anomalyId;
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
