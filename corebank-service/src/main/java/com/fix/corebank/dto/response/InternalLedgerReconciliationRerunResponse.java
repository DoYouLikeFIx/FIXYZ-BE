package com.fix.corebank.dto.response;

import com.fix.corebank.vo.LedgerReconciliationRerunResult;
import java.time.Instant;

public class InternalLedgerReconciliationRerunResponse {

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

  private InternalLedgerReconciliationRerunResponse(
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

  public static InternalLedgerReconciliationRerunResponse from(LedgerReconciliationRerunResult result) {
    return new InternalLedgerReconciliationRerunResponse(
        result.getCaseId(),
        result.getPreviousStatus(),
        result.getCurrentStatus(),
        result.isChanged(),
        result.getEventId(),
        result.getRerunRunId(),
        result.isAnomalyStillPresent(),
        result.getReason(),
        result.getActor(),
        result.getContext(),
        result.getAsOf()
    );
  }

  public Long getCaseId() { return caseId; }
  public String getPreviousStatus() { return previousStatus; }
  public String getCurrentStatus() { return currentStatus; }
  public boolean isChanged() { return changed; }
  public Long getEventId() { return eventId; }
  public Long getRerunRunId() { return rerunRunId; }
  public boolean isAnomalyStillPresent() { return anomalyStillPresent; }
  public String getReason() { return reason; }
  public String getActor() { return actor; }
  public String getContext() { return context; }
  public Instant getAsOf() { return asOf; }
}
