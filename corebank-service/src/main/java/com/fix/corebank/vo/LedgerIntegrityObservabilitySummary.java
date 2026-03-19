package com.fix.corebank.vo;

import java.time.Instant;
import java.util.List;

public class LedgerIntegrityObservabilitySummary {

  private final Long latestRunId;
  private final Instant latestRunCheckedAt;
  private final Boolean latestRunPassed;
  private final Integer latestRunAnomalyCount;
  private final String latestRunSummaryMessage;
  private final long unresolvedAnomalyCount;
  private final long repairPendingCount;
  private final long criticalAnomalyCount;
  private final boolean staleLastRun;
  private final Long latestFailedRunId;
  private final List<LedgerIntegrityFailedIdentifier> latestFailedIdentifiers;

  private LedgerIntegrityObservabilitySummary(
      Long latestRunId,
      Instant latestRunCheckedAt,
      Boolean latestRunPassed,
      Integer latestRunAnomalyCount,
      String latestRunSummaryMessage,
      long unresolvedAnomalyCount,
      long repairPendingCount,
      long criticalAnomalyCount,
      boolean staleLastRun,
      Long latestFailedRunId,
      List<LedgerIntegrityFailedIdentifier> latestFailedIdentifiers
  ) {
    this.latestRunId = latestRunId;
    this.latestRunCheckedAt = latestRunCheckedAt;
    this.latestRunPassed = latestRunPassed;
    this.latestRunAnomalyCount = latestRunAnomalyCount;
    this.latestRunSummaryMessage = latestRunSummaryMessage;
    this.unresolvedAnomalyCount = unresolvedAnomalyCount;
    this.repairPendingCount = repairPendingCount;
    this.criticalAnomalyCount = criticalAnomalyCount;
    this.staleLastRun = staleLastRun;
    this.latestFailedRunId = latestFailedRunId;
    this.latestFailedIdentifiers = List.copyOf(latestFailedIdentifiers);
  }

  public static LedgerIntegrityObservabilitySummary of(
      Long latestRunId,
      Instant latestRunCheckedAt,
      Boolean latestRunPassed,
      Integer latestRunAnomalyCount,
      String latestRunSummaryMessage,
      long unresolvedAnomalyCount,
      long repairPendingCount,
      long criticalAnomalyCount,
      boolean staleLastRun,
      Long latestFailedRunId,
      List<LedgerIntegrityFailedIdentifier> latestFailedIdentifiers
  ) {
    return new LedgerIntegrityObservabilitySummary(
        latestRunId,
        latestRunCheckedAt,
        latestRunPassed,
        latestRunAnomalyCount,
        latestRunSummaryMessage,
        unresolvedAnomalyCount,
        repairPendingCount,
        criticalAnomalyCount,
        staleLastRun,
        latestFailedRunId,
        latestFailedIdentifiers
    );
  }

  public Long getLatestRunId() {
    return latestRunId;
  }

  public Instant getLatestRunCheckedAt() {
    return latestRunCheckedAt;
  }

  public Boolean getLatestRunPassed() {
    return latestRunPassed;
  }

  public Integer getLatestRunAnomalyCount() {
    return latestRunAnomalyCount;
  }

  public String getLatestRunSummaryMessage() {
    return latestRunSummaryMessage;
  }

  public long getUnresolvedAnomalyCount() {
    return unresolvedAnomalyCount;
  }

  public long getRepairPendingCount() {
    return repairPendingCount;
  }

  public long getCriticalAnomalyCount() {
    return criticalAnomalyCount;
  }

  public boolean isStaleLastRun() {
    return staleLastRun;
  }

  public Long getLatestFailedRunId() {
    return latestFailedRunId;
  }

  public List<LedgerIntegrityFailedIdentifier> getLatestFailedIdentifiers() {
    return latestFailedIdentifiers;
  }
}
