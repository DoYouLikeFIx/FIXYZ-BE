package com.fix.corebank.vo;

public class LedgerReconciliationRepairCommand {

  private final Long caseId;
  private final String repairKey;
  private final String repairType;
  private final String reason;
  private final String actor;
  private final String context;
  private final String correlationId;

  private LedgerReconciliationRepairCommand(
      Long caseId,
      String repairKey,
      String repairType,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    this.caseId = caseId;
    this.repairKey = repairKey;
    this.repairType = repairType;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
    this.correlationId = correlationId;
  }

  public static LedgerReconciliationRepairCommand of(
      Long caseId,
      String repairKey,
      String repairType,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    return new LedgerReconciliationRepairCommand(caseId, repairKey, repairType, reason, actor, context, correlationId);
  }

  public Long getCaseId() {
    return caseId;
  }

  public String getRepairKey() {
    return repairKey;
  }

  public String getRepairType() {
    return repairType;
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
