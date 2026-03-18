package com.fix.corebank.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.corebank.vo.LedgerReconciliationRepairResult;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalLedgerReconciliationRepairResponse {

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

  private InternalLedgerReconciliationRepairResponse(
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

  public static InternalLedgerReconciliationRepairResponse from(LedgerReconciliationRepairResult result) {
    return new InternalLedgerReconciliationRepairResponse(
        result.getRepairId(),
        result.getCaseId(),
        result.getRepairKey(),
        result.getRepairType(),
        result.getOutcome(),
        result.isIdempotent(),
        result.isMutated(),
        result.getCaseStatus(),
        result.getRerunRunId(),
        result.getRerunCaseStatus(),
        result.getSummaryMessage(),
        result.getAsOf()
    );
  }

  public Long getRepairId() { return repairId; }
  public Long getCaseId() { return caseId; }
  public String getRepairKey() { return repairKey; }
  public String getRepairType() { return repairType; }
  public String getOutcome() { return outcome; }
  public boolean isIdempotent() { return idempotent; }
  public boolean isMutated() { return mutated; }
  public String getCaseStatus() { return caseStatus; }
  public Long getRerunRunId() { return rerunRunId; }
  public String getRerunCaseStatus() { return rerunCaseStatus; }
  public String getSummaryMessage() { return summaryMessage; }
  public Instant getAsOf() { return asOf; }
}
