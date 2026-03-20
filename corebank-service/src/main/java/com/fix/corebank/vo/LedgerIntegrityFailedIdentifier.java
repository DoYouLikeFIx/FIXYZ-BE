package com.fix.corebank.vo;

import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import com.fix.corebank.entity.LedgerReconciliationCase;

public class LedgerIntegrityFailedIdentifier {

  private final Long anomalyId;
  private final String anomalyType;
  private final Long accountId;
  private final String symbol;
  private final Long positionId;
  private final Long executionId;
  private final Long orderId;
  private final String clOrdId;
  private final Long journalEntryId;
  private final Long ledgerEntryId;

  private LedgerIntegrityFailedIdentifier(
      Long anomalyId,
      String anomalyType,
      Long accountId,
      String symbol,
      Long positionId,
      Long executionId,
      Long orderId,
      String clOrdId,
      Long journalEntryId,
      Long ledgerEntryId
  ) {
    this.anomalyId = anomalyId;
    this.anomalyType = anomalyType;
    this.accountId = accountId;
    this.symbol = symbol;
    this.positionId = positionId;
    this.executionId = executionId;
    this.orderId = orderId;
    this.clOrdId = clOrdId;
    this.journalEntryId = journalEntryId;
    this.ledgerEntryId = ledgerEntryId;
  }

  public static LedgerIntegrityFailedIdentifier from(LedgerIntegrityAnomalyRecord anomaly) {
    return new LedgerIntegrityFailedIdentifier(
        anomaly.getId(),
        anomaly.getType(),
        anomaly.getAccountId(),
        anomaly.getSymbol(),
        anomaly.getPositionId(),
        anomaly.getExecutionId(),
        anomaly.getOrderId(),
        anomaly.getClOrdId(),
        anomaly.getJournalEntryId(),
        anomaly.getLedgerEntryId()
    );
  }

  public static LedgerIntegrityFailedIdentifier from(LedgerReconciliationCase reconciliationCase) {
    return new LedgerIntegrityFailedIdentifier(
        reconciliationCase.getAnomalyId(),
        reconciliationCase.getAnomalyType(),
        reconciliationCase.getAccountId(),
        reconciliationCase.getSymbol(),
        reconciliationCase.getPositionId(),
        reconciliationCase.getExecutionId(),
        reconciliationCase.getOrderId(),
        reconciliationCase.getClOrdId(),
        reconciliationCase.getJournalEntryId(),
        reconciliationCase.getLedgerEntryId()
    );
  }

  public Long getAnomalyId() {
    return anomalyId;
  }

  public String getAnomalyType() {
    return anomalyType;
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
}
