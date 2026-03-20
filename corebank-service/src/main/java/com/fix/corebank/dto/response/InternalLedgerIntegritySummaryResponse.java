package com.fix.corebank.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fix.corebank.vo.LedgerIntegrityFailedIdentifier;
import com.fix.corebank.vo.LedgerIntegrityObservabilitySummary;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalLedgerIntegritySummaryResponse {

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
  private final List<FailedIdentifierResponse> latestFailedIdentifiers;

  private InternalLedgerIntegritySummaryResponse(
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
      List<FailedIdentifierResponse> latestFailedIdentifiers
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

  public static InternalLedgerIntegritySummaryResponse from(LedgerIntegrityObservabilitySummary summary) {
    return new InternalLedgerIntegritySummaryResponse(
        summary.getLatestRunId(),
        summary.getLatestRunCheckedAt(),
        summary.getLatestRunPassed(),
        summary.getLatestRunAnomalyCount(),
        summary.getLatestRunSummaryMessage(),
        summary.getUnresolvedAnomalyCount(),
        summary.getRepairPendingCount(),
        summary.getCriticalAnomalyCount(),
        summary.isStaleLastRun(),
        summary.getLatestFailedRunId(),
        summary.getLatestFailedIdentifiers().stream()
            .map(FailedIdentifierResponse::from)
            .toList()
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

  public List<FailedIdentifierResponse> getLatestFailedIdentifiers() {
    return latestFailedIdentifiers;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class FailedIdentifierResponse {

    private final Long anomalyId;
    private final String anomalyType;
    private final Long accountId;
    private final String symbol;
    private final Long positionId;
    private final Long executionId;
    private final Long orderId;
    private final String clOrdId;
    private final Long journalEntryId;
    private final Long ledgerEntryId;

    private FailedIdentifierResponse(
        Long anomalyId,
        String anomalyType,
        Long accountId,
        String symbol,
        Long positionId,
        Long executionId,
        Long orderId,
        String clOrdId,
        Long journalEntryId,
        Long ledgerEntryId
    ) {
      this.anomalyId = anomalyId;
      this.anomalyType = anomalyType;
      this.accountId = accountId;
      this.symbol = symbol;
      this.positionId = positionId;
      this.executionId = executionId;
      this.orderId = orderId;
      this.clOrdId = clOrdId;
      this.journalEntryId = journalEntryId;
      this.ledgerEntryId = ledgerEntryId;
    }

    public static FailedIdentifierResponse from(LedgerIntegrityFailedIdentifier identifier) {
      return new FailedIdentifierResponse(
          identifier.getAnomalyId(),
          identifier.getAnomalyType(),
          identifier.getAccountId(),
          identifier.getSymbol(),
          identifier.getPositionId(),
          identifier.getExecutionId(),
          identifier.getOrderId(),
          identifier.getClOrdId(),
          identifier.getJournalEntryId(),
          identifier.getLedgerEntryId()
      );
    }

    public Long getAnomalyId() {
      return anomalyId;
    }

    public String getAnomalyType() {
      return anomalyType;
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
}
