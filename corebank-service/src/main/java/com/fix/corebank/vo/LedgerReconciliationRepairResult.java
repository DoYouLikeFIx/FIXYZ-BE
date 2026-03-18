package com.fix.corebank.vo;

import java.time.Instant;

public class LedgerReconciliationRepairResult {

  private final Long repairId;
  private final Long caseId;
  private final String repairKey;
  private final String repairType;
  private final String outcome;
  private final boolean idempotent;
  private final boolean mutated;
  private final String caseStatus;
  private final Long rerunRunId;
  private final String rerunCaseStatus;
  private final String summaryMessage;
  private final Instant asOf;

  private LedgerReconciliationRepairResult(
      Long repairId,
      Long caseId,
      String repairKey,
      String repairType,
      String outcome,
      boolean idempotent,
      boolean mutated,
      String caseStatus,
      Long rerunRunId,
      String rerunCaseStatus,
      String summaryMessage,
      Instant asOf
  ) {
    this.repairId = repairId;
    this.caseId = caseId;
    this.repairKey = repairKey;
    this.repairType = repairType;
    this.outcome = outcome;
    this.idempotent = idempotent;
    this.mutated = mutated;
    this.caseStatus = caseStatus;
    this.rerunRunId = rerunRunId;
    this.rerunCaseStatus = rerunCaseStatus;
    this.summaryMessage = summaryMessage;
    this.asOf = asOf;
  }

  public static LedgerReconciliationRepairResult of(
      Long repairId,
      Long caseId,
      String repairKey,
      String repairType,
      String outcome,
      boolean idempotent,
      boolean mutated,
      String caseStatus,
      Long rerunRunId,
      String rerunCaseStatus,
      String summaryMessage,
      Instant asOf
  ) {
    return new LedgerReconciliationRepairResult(
        repairId,
        caseId,
        repairKey,
        repairType,
        outcome,
        idempotent,
        mutated,
        caseStatus,
        rerunRunId,
        rerunCaseStatus,
        summaryMessage,
        asOf
    );
  }

  public Long getRepairId() {
    return repairId;
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

  public String getOutcome() {
    return outcome;
  }

  public boolean isIdempotent() {
    return idempotent;
  }

  public boolean isMutated() {
    return mutated;
  }

  public String getCaseStatus() {
    return caseStatus;
  }

  public Long getRerunRunId() {
    return rerunRunId;
  }

  public String getRerunCaseStatus() {
    return rerunCaseStatus;
  }

  public String getSummaryMessage() {
    return summaryMessage;
  }

  public Instant getAsOf() {
    return asOf;
  }
}
