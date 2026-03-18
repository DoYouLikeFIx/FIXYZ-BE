package com.fix.corebank.vo;

import java.time.Instant;
import java.util.List;

public class LedgerIntegrityCheckResult {

  private final Long runId;
  private final Instant checkedAt;
  private final boolean passed;
  private final int anomalyCount;
  private final List<LedgerIntegrityAnomaly> anomalies;

  private LedgerIntegrityCheckResult(
      Long runId,
      Instant checkedAt,
      boolean passed,
      int anomalyCount,
      List<LedgerIntegrityAnomaly> anomalies
  ) {
    this.runId = runId;
    this.checkedAt = checkedAt;
    this.passed = passed;
    this.anomalyCount = anomalyCount;
    this.anomalies = anomalies;
  }

  public static LedgerIntegrityCheckResult of(Instant checkedAt, List<LedgerIntegrityAnomaly> anomalies) {
    List<LedgerIntegrityAnomaly> immutableAnomalies = List.copyOf(anomalies);
    return new LedgerIntegrityCheckResult(
        null,
        checkedAt,
        immutableAnomalies.isEmpty(),
        immutableAnomalies.size(),
        immutableAnomalies
    );
  }

  public LedgerIntegrityCheckResult withRunId(Long runId) {
    return new LedgerIntegrityCheckResult(
        runId,
        checkedAt,
        passed,
        anomalyCount,
        anomalies
    );
  }

  public Long getRunId() {
    return runId;
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

  public List<LedgerIntegrityAnomaly> getAnomalies() {
    return anomalies;
  }
}
