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
@Table(name = "ledger_reconciliation_case_events")
public class LedgerReconciliationCaseEvent extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "case_id", nullable = false)
  private Long caseId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 32)
  private LedgerReconciliationCaseEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_status", length = 32)
  private LedgerReconciliationCaseStatus previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_status", nullable = false, length = 32)
  private LedgerReconciliationCaseStatus newStatus;

  @Column(name = "reason", nullable = false, length = 255)
  private String reason;

  @Column(name = "actor", nullable = false, length = 64)
  private String actor;

  @Column(name = "context", length = 255)
  private String context;

  @Column(name = "correlation_id", length = 64)
  private String correlationId;

  protected LedgerReconciliationCaseEvent() {
  }

  private LedgerReconciliationCaseEvent(
      Long caseId,
      LedgerReconciliationCaseEventType eventType,
      LedgerReconciliationCaseStatus previousStatus,
      LedgerReconciliationCaseStatus newStatus,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    this.caseId = caseId;
    this.eventType = eventType;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
    this.correlationId = correlationId;
  }

  public static LedgerReconciliationCaseEvent created(
      Long caseId,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    return new LedgerReconciliationCaseEvent(
        caseId,
        LedgerReconciliationCaseEventType.CREATED,
        null,
        LedgerReconciliationCaseStatus.NEW,
        reason,
        actor,
        context,
        correlationId
    );
  }

  public static LedgerReconciliationCaseEvent statusChanged(
      Long caseId,
      LedgerReconciliationCaseStatus previousStatus,
      LedgerReconciliationCaseStatus newStatus,
      String reason,
      String actor,
      String context,
      String correlationId
  ) {
    return new LedgerReconciliationCaseEvent(
        caseId,
        LedgerReconciliationCaseEventType.STATUS_CHANGED,
        previousStatus,
        newStatus,
        reason,
        actor,
        context,
        correlationId
    );
  }

  public Long getId() {
    return id;
  }

  public Long getCaseId() {
    return caseId;
  }

  public LedgerReconciliationCaseEventType getEventType() {
    return eventType;
  }

  public LedgerReconciliationCaseStatus getPreviousStatus() {
    return previousStatus;
  }

  public LedgerReconciliationCaseStatus getNewStatus() {
    return newStatus;
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
