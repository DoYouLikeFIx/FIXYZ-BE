package com.fix.corebank.entity;

import com.fix.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ledger_integrity_runs")
public class LedgerIntegrityRun extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "checked_at", nullable = false)
  private Instant checkedAt;

  @Column(name = "passed", nullable = false)
  private boolean passed;

  @Column(name = "anomaly_count", nullable = false)
  private int anomalyCount;

  @Column(name = "summary_message", length = 500)
  private String summaryMessage;

  protected LedgerIntegrityRun() {
  }

  private LedgerIntegrityRun(
      Instant checkedAt,
      boolean passed,
      int anomalyCount,
      String summaryMessage
  ) {
    this.checkedAt = checkedAt;
    this.passed = passed;
    this.anomalyCount = anomalyCount;
    this.summaryMessage = summaryMessage;
  }

  public static LedgerIntegrityRun of(
      Instant checkedAt,
      boolean passed,
      int anomalyCount,
      String summaryMessage
  ) {
    return new LedgerIntegrityRun(checkedAt, passed, anomalyCount, summaryMessage);
  }

  public Long getId() {
    return id;
  }

  public Instant getCheckedAt() {
    return checkedAt;
  }

  public boolean isPassed() {
    return passed;
  }

  public int getAnomalyCount() {
    return anomalyCount;
  }

  public String getSummaryMessage() {
    return summaryMessage;
  }
}
