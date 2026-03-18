package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ledger_reconciliation_cases")
public class LedgerReconciliationCase extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "anomaly_id", nullable = false)
  private Long anomalyId;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private LedgerReconciliationCaseStatus status;

  @Column(name = "anomaly_type", nullable = false, length = 64)
  private String anomalyType;

  @Column(name = "summary_message", nullable = false, length = 500)
  private String summaryMessage;

  @Column(name = "account_id")
  private Long accountId;

  @Column(name = "symbol", length = 32)
  private String symbol;

  @Column(name = "position_id")
  private Long positionId;

  @Column(name = "execution_id")
  private Long executionId;

  @Column(name = "order_id")
  private Long orderId;

  @Column(name = "cl_ord_id", length = 64)
  private String clOrdId;

  @Column(name = "journal_entry_id")
  private Long journalEntryId;

  @Column(name = "ledger_entry_id")
  private Long ledgerEntryId;

  @Column(name = "last_transition_at", nullable = false)
  private Instant lastTransitionAt;

  protected LedgerReconciliationCase() {
  }

  private LedgerReconciliationCase(
      Long anomalyId,
      Long runId,
      LedgerReconciliationCaseStatus status,
      String anomalyType,
      String summaryMessage,
      Long accountId,
      String symbol,
      Long positionId,
      Long executionId,
      Long orderId,
      String clOrdId,
      Long journalEntryId,
      Long ledgerEntryId,
      Instant lastTransitionAt
  ) {
    this.anomalyId = anomalyId;
    this.runId = runId;
    this.status = status;
    this.anomalyType = anomalyType;
    this.summaryMessage = summaryMessage;
    this.accountId = accountId;
    this.symbol = symbol;
    this.positionId = positionId;
    this.executionId = executionId;
    this.orderId = orderId;
    this.clOrdId = clOrdId;
    this.journalEntryId = journalEntryId;
    this.ledgerEntryId = ledgerEntryId;
    this.lastTransitionAt = lastTransitionAt;
  }

  public static LedgerReconciliationCase openFromAnomaly(LedgerIntegrityAnomalyRecord anomaly, Instant openedAt) {
    if (anomaly == null) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "anomaly is required");
    }
    Instant normalizedOpenedAt = openedAt == null ? Instant.now() : openedAt;
    return new LedgerReconciliationCase(
        anomaly.getId(),
        anomaly.getRunId(),
        LedgerReconciliationCaseStatus.NEW,
        anomaly.getType(),
        anomaly.getMessage(),
        anomaly.getAccountId(),
        anomaly.getSymbol(),
        anomaly.getPositionId(),
        anomaly.getExecutionId(),
        anomaly.getOrderId(),
        anomaly.getClOrdId(),
        anomaly.getJournalEntryId(),
        anomaly.getLedgerEntryId(),
        normalizedOpenedAt
    );
  }

  public boolean transitionTo(LedgerReconciliationCaseStatus targetStatus, Instant transitionedAt) {
    if (targetStatus == null) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "reconciliation case targetStatus is required"
      );
    }
    if (status == targetStatus) {
      return false;
    }
    if (!canTransitionTo(targetStatus)) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "unsupported reconciliation case transition: " + status + " -> " + targetStatus
      );
    }
    this.status = targetStatus;
    this.lastTransitionAt = transitionedAt == null ? Instant.now() : transitionedAt;
    return true;
  }

  public boolean isTerminal() {
    return status.isTerminal();
  }

  private boolean canTransitionTo(LedgerReconciliationCaseStatus targetStatus) {
    return switch (status) {
      case NEW -> targetStatus == LedgerReconciliationCaseStatus.ACKNOWLEDGED
          || targetStatus == LedgerReconciliationCaseStatus.WAIVED
          || targetStatus == LedgerReconciliationCaseStatus.REPAIR_PENDING;
      case ACKNOWLEDGED -> targetStatus == LedgerReconciliationCaseStatus.WAIVED
          || targetStatus == LedgerReconciliationCaseStatus.REPAIR_PENDING
          || targetStatus == LedgerReconciliationCaseStatus.RESOLVED;
      case REPAIR_PENDING -> targetStatus == LedgerReconciliationCaseStatus.ACKNOWLEDGED
          || targetStatus == LedgerReconciliationCaseStatus.RESOLVED
          || targetStatus == LedgerReconciliationCaseStatus.REOPENED;
      case WAIVED -> targetStatus == LedgerReconciliationCaseStatus.REOPENED;
      case RESOLVED -> targetStatus == LedgerReconciliationCaseStatus.REOPENED;
      case REOPENED -> targetStatus == LedgerReconciliationCaseStatus.ACKNOWLEDGED
          || targetStatus == LedgerReconciliationCaseStatus.WAIVED
          || targetStatus == LedgerReconciliationCaseStatus.REPAIR_PENDING
          || targetStatus == LedgerReconciliationCaseStatus.RESOLVED;
    };
  }

  public Long getId() {
    return id;
  }

  public Long getAnomalyId() {
    return anomalyId;
  }

  public Long getRunId() {
    return runId;
  }

  public LedgerReconciliationCaseStatus getStatus() {
    return status;
  }

  public String getAnomalyType() {
    return anomalyType;
  }

  public String getSummaryMessage() {
    return summaryMessage;
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getSymbol() {
    return symbol;
  }

  public Long getPositionId() {
    return positionId;
  }

  public Long getExecutionId() {
    return executionId;
  }

  public Long getOrderId() {
    return orderId;
  }

  public String getClOrdId() {
    return clOrdId;
  }

  public Long getJournalEntryId() {
    return journalEntryId;
  }

  public Long getLedgerEntryId() {
    return ledgerEntryId;
  }

  public Instant getLastTransitionAt() {
    return lastTransitionAt;
  }
}
