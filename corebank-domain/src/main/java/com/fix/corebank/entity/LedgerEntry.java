package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "journal_entry_id", nullable = false)
  private Long journalEntryId;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "ledger_type", nullable = false, length = 32)
  private String ledgerType;

  @Column(name = "direction", nullable = false, length = 16)
  private String direction;

  @Column(name = "amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  protected LedgerEntry() {
  }

  private LedgerEntry(Long journalEntryId, Long accountId, String ledgerType, String direction, BigDecimal amount) {
    this.journalEntryId = journalEntryId;
    this.accountId = accountId;
    this.ledgerType = ledgerType;
    this.direction = direction;
    this.amount = amount;
  }

  public static LedgerEntry of(
      Long journalEntryId,
      Long accountId,
      String ledgerType,
      String direction,
      BigDecimal amount
  ) {
    return new LedgerEntry(journalEntryId, accountId, ledgerType, direction, amount);
  }

  public Long getId() {
    return id;
  }

  public Long getJournalEntryId() {
    return journalEntryId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public String getLedgerType() {
    return ledgerType;
  }

  public String getDirection() {
    return direction;
  }

  public BigDecimal getAmount() {
    return amount;
  }
}
