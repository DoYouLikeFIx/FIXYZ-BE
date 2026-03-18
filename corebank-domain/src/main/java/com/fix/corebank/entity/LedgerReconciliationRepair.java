package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_reconciliation_repairs")
public class LedgerReconciliationRepair extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "case_id", nullable = false)
  private Long caseId;

  @Column(name = "repair_key", nullable = false, length = 64)
  private String repairKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "repair_type", nullable = false, length = 64)
  private LedgerReconciliationRepairType repairType;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, length = 16)
  private LedgerReconciliationRepairOutcome outcome;

  @Column(name = "mutated", nullable = false)
  private boolean mutated;

  @Column(name = "reason", nullable = false, length = 255)
  private String reason;

  @Column(name = "actor", nullable = false, length = 64)
  private String actor;

  @Column(name = "context", length = 255)
  private String context;

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  @Column(name = "summary_message", nullable = false, length = 500)
  private String summaryMessage;

  @Column(name = "rerun_run_id")
  private Long rerunRunId;

  @Column(name = "rerun_case_status", length = 32)
  private String rerunCaseStatus;

  protected LedgerReconciliationRepair() {
  }

  private LedgerReconciliationRepair(
      Long caseId,
      String repairKey,
      LedgerReconciliationRepairType repairType,
      LedgerReconciliationRepairOutcome outcome,
      boolean mutated,
      String reason,
      String actor,
      String context,
      String correlationId,
      String summaryMessage
  ) {
    this.caseId = caseId;
    this.repairKey = repairKey;
    this.repairType = repairType;
    this.outcome = outcome;
    this.mutated = mutated;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
    this.correlationId = correlationId;
    this.summaryMessage = summaryMessage;
  }

  public static LedgerReconciliationRepair of(
      Long caseId,
      String repairKey,
      LedgerReconciliationRepairType repairType,
      LedgerReconciliationRepairOutcome outcome,
      boolean mutated,
      String reason,
      String actor,
      String context,
      String correlationId,
      String summaryMessage
  ) {
    return new LedgerReconciliationRepair(
        caseId,
        repairKey,
        repairType,
        outcome,
        mutated,
        reason,
        actor,
        context,
        correlationId,
        summaryMessage
    );
  }

  public void linkRerun(Long rerunRunId, LedgerReconciliationCaseStatus rerunCaseStatus) {
    this.rerunRunId = rerunRunId;
    this.rerunCaseStatus = rerunCaseStatus == null ? null : rerunCaseStatus.name();
  }

  public Long getId() {
    return id;
  }

  public Long getCaseId() {
    return caseId;
  }

  public String getRepairKey() {
    return repairKey;
  }

  public LedgerReconciliationRepairType getRepairType() {
    return repairType;
  }

  public LedgerReconciliationRepairOutcome getOutcome() {
    return outcome;
  }

  public boolean isMutated() {
    return mutated;
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

  public String getSummaryMessage() {
    return summaryMessage;
  }

  public Long getRerunRunId() {
    return rerunRunId;
  }

  public String getRerunCaseStatus() {
    return rerunCaseStatus;
  }
}
