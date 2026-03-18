package com.fix.corebank.vo;

import java.time.Instant;

public class LedgerReconciliationRerunResult {

  private final Long caseId;
  private final String previousStatus;
  private final String currentStatus;
  private final boolean changed;
  private final Long eventId;
  private final Long rerunRunId;
  private final boolean anomalyStillPresent;
  private final String reason;
  private final String actor;
  private final String context;
  private final Instant asOf;

  private LedgerReconciliationRerunResult(
      Long caseId,
      String previousStatus,
      String currentStatus,
      boolean changed,
      Long eventId,
      Long rerunRunId,
      boolean anomalyStillPresent,
      String reason,
      String actor,
      String context,
      Instant asOf
  ) {
    this.caseId = caseId;
    this.previousStatus = previousStatus;
    this.currentStatus = currentStatus;
    this.changed = changed;
    this.eventId = eventId;
    this.rerunRunId = rerunRunId;
    this.anomalyStillPresent = anomalyStillPresent;
    this.reason = reason;
    this.actor = actor;
    this.context = context;
    this.asOf = asOf;
  }

  public static LedgerReconciliationRerunResult of(
      Long caseId,
      String previousStatus,
      String currentStatus,
      boolean changed,
      Long eventId,
      Long rerunRunId,
      boolean anomalyStillPresent,
      String reason,
      String actor,
      String context,
      Instant asOf
  ) {
    return new LedgerReconciliationRerunResult(
        caseId,
        previousStatus,
        currentStatus,
        changed,
        eventId,
        rerunRunId,
        anomalyStillPresent,
        reason,
        actor,
        context,
        asOf
    );
  }

  public Long getCaseId() {
    return caseId;
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

  public Long getEventId() {
    return eventId;
  }

  public Long getRerunRunId() {
    return rerunRunId;
  }

  public boolean isAnomalyStillPresent() {
    return anomalyStillPresent;
  }

  public String getReason() {
    return reason;
  }

  public String getActor() {
    return actor;
  }

  public String getContext() {
    return context;
  }

  public Instant getAsOf() {
    return asOf;
  }
}
