package com.fix.corebank.vo;

public class LedgerIntegrityAnomaly {

  private final String type;
  private final String message;
  private final Long accountId;
  private final String symbol;
  private final Long positionId;
  private final Long executionId;
  private final Long orderId;
  private final String clOrdId;
  private final Long journalEntryId;
  private final Long ledgerEntryId;

  private LedgerIntegrityAnomaly(
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

  public static LedgerIntegrityAnomaly of(
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
    return new LedgerIntegrityAnomaly(
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
