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
@Table(name = "journal_entries")
public class JournalEntry extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(name = "entry_type", nullable = false, length = 32)
  private String entryType;

  @Column(name = "amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(name = "memo", length = 255)
  private String memo;

  protected JournalEntry() {
  }

  private JournalEntry(Long orderId, String entryType, BigDecimal amount, String memo) {
    this.orderId = orderId;
    this.entryType = entryType;
    this.amount = amount;
    this.memo = memo;
  }

  public static JournalEntry of(Long orderId, String entryType, BigDecimal amount, String memo) {
    return new JournalEntry(orderId, entryType, amount, memo);
  }

  public Long getId() {
    return id;
  }

  public Long getOrderId() {
    return orderId;
  }

  public String getEntryType() {
    return entryType;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getMemo() {
    return memo;
  }
}
