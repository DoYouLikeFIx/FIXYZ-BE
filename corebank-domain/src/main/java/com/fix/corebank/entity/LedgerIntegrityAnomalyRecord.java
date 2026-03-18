package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_integrity_anomalies")
public class LedgerIntegrityAnomalyRecord extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Column(name = "type", nullable = false, length = 64)
  private String type;

  @Column(name = "message", nullable = false, length = 500)
  private String message;

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

  protected LedgerIntegrityAnomalyRecord() {
  }

  private LedgerIntegrityAnomalyRecord(
      Long runId,
      String type,
      String message,
      Long accountId,
      String symbol,
      Long positionId,
      Long executionId,
      Long orderId,
      String clOrdId,
      Long journalEntryId,
      Long ledgerEntryId
  ) {
    this.runId = runId;
    this.type = type;
    this.message = message;
    this.accountId = accountId;
    this.symbol = symbol;
    this.positionId = positionId;
    this.executionId = executionId;
    this.orderId = orderId;
    this.clOrdId = clOrdId;
    this.journalEntryId = journalEntryId;
    this.ledgerEntryId = ledgerEntryId;
  }

  public static LedgerIntegrityAnomalyRecord of(
      Long runId,
      String type,
      String message,
      Long accountId,
      String symbol,
      Long positionId,
      Long executionId,
      Long orderId,
      String clOrdId,
      Long journalEntryId,
      Long ledgerEntryId
  ) {
    return new LedgerIntegrityAnomalyRecord(
        runId,
        type,
        message,
        accountId,
        symbol,
        positionId,
        executionId,
        orderId,
        clOrdId,
        journalEntryId,
        ledgerEntryId
    );
  }

  public Long getId() {
    return id;
  }

  public Long getRunId() {
    return runId;
  }

  public String getType() {
    return type;
  }

  public String getMessage() {
    return message;
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
