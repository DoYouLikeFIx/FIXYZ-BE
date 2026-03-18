package com.fix.corebank.vo;

import java.time.Instant;

public class LedgerReconciliationCaseResult {

  private final Long caseId;
  private final Long anomalyId;
  private final Long runId;
  private final String previousStatus;
  private final String currentStatus;
  private final boolean changed;
  private final boolean created;
  private final Long eventId;
  private final String anomalyType;
  private final String summaryMessage;
  private final Long accountId;
  private final String symbol;
  private final Long positionId;
  private final Long executionId;
  private final Long orderId;
  private final String clOrdId;
  private final Long journalEntryId;
  private final Long ledgerEntryId;
  private final Instant asOf;

  private LedgerReconciliationCaseResult(
      Long caseId,
      Long anomalyId,
      Long runId,
      String previousStatus,
      String currentStatus,
      boolean changed,
      boolean created,
      Long eventId,
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
      Instant asOf
  ) {
    this.caseId = caseId;
    this.anomalyId = anomalyId;
    this.runId = runId;
    this.previousStatus = previousStatus;
    this.currentStatus = currentStatus;
    this.changed = changed;
    this.created = created;
    this.eventId = eventId;
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
    this.asOf = asOf;
  }

  public static LedgerReconciliationCaseResult of(
      Long caseId,
      Long anomalyId,
      Long runId,
      String previousStatus,
      String currentStatus,
      boolean changed,
      boolean created,
      Long eventId,
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
      Instant asOf
  ) {
    return new LedgerReconciliationCaseResult(
        caseId,
        anomalyId,
        runId,
        previousStatus,
        currentStatus,
        changed,
        created,
        eventId,
        anomalyType,
        summaryMessage,
        accountId,
        symbol,
        positionId,
        executionId,
        orderId,
        clOrdId,
        journalEntryId,
        ledgerEntryId,
        asOf
    );
  }

  public Long getCaseId() {
    return caseId;
  }

  public Long getAnomalyId() {
    return anomalyId;
  }

  public Long getRunId() {
    return runId;
  }

  public String getPreviousStatus() {
    return previousStatus;
  }

  public String getCurrentStatus() {
    return currentStatus;
  }

  public boolean isChanged() {
    return changed;
  }

  public boolean isCreated() {
    return created;
  }

  public Long getEventId() {
    return eventId;
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

  public Instant getAsOf() {
    return asOf;
  }
}
