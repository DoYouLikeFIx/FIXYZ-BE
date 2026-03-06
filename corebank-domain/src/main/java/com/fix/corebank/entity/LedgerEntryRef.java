package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_entry_refs")
public class LedgerEntryRef extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "ledger_entry_id", nullable = false)
  private Long ledgerEntryId;

  @Column(name = "ref_type", nullable = false, length = 32)
  private String refType;

  @Column(name = "ref_value", nullable = false, length = 128)
  private String refValue;

  protected LedgerEntryRef() {
  }

  private LedgerEntryRef(Long ledgerEntryId, String refType, String refValue) {
    this.ledgerEntryId = ledgerEntryId;
    this.refType = refType;
    this.refValue = refValue;
  }

  public static LedgerEntryRef of(Long ledgerEntryId, String refType, String refValue) {
    return new LedgerEntryRef(ledgerEntryId, refType, refValue);
  }

  public Long getId() {
    return id;
  }

  public Long getLedgerEntryId() {
    return ledgerEntryId;
  }

  public String getRefType() {
    return refType;
  }

  public String getRefValue() {
    return refValue;
  }
}
