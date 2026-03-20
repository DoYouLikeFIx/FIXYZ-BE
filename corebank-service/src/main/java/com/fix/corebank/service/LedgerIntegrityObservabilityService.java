package com.fix.corebank.service;

import com.fix.corebank.config.LedgerIntegrityObservabilityProperties;
import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import com.fix.corebank.entity.LedgerIntegrityRun;
import com.fix.corebank.entity.LedgerReconciliationCase;
import com.fix.corebank.entity.LedgerReconciliationCaseStatus;
import com.fix.corebank.repository.LedgerIntegrityAnomalyRecordRepository;
import com.fix.corebank.repository.LedgerIntegrityRunRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseRepository;
import com.fix.corebank.vo.LedgerIntegrityFailedIdentifier;
import com.fix.corebank.vo.LedgerIntegrityObservabilitySummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LedgerIntegrityObservabilityService {

  private static final Logger log = LoggerFactory.getLogger(LedgerIntegrityObservabilityService.class);

  private static final Set<LedgerReconciliationCaseStatus> OPEN_CASE_STATUSES = EnumSet.of(
      LedgerReconciliationCaseStatus.NEW,
      LedgerReconciliationCaseStatus.ACKNOWLEDGED,
      LedgerReconciliationCaseStatus.REPAIR_PENDING,
      LedgerReconciliationCaseStatus.REOPENED
  );
  private static final Set<LedgerReconciliationCaseStatus> REPAIR_PENDING_STATUSES =
      EnumSet.of(LedgerReconciliationCaseStatus.REPAIR_PENDING);

  private static final Set<String> CRITICAL_ANOMALY_TYPES = Set.of(
      "NEGATIVE_POSITION",
      "ORPHAN_EXECUTION",
      "JOURNAL_LEDGER_COUNT_MISMATCH",
      "JOURNAL_LEDGER_BALANCE_MISMATCH"
  );

  private static final int MAX_ALERT_IDENTIFIERS = 10;
  private static final int MAX_SUMMARY_IDENTIFIERS = 25;
  private static final List<String> LEDGER_INTEGRITY_TABLE_NAMES = List.of(
      "ledger_integrity_runs",
      "ledger_integrity_anomaly_records",
      "ledger_reconciliation_cases"
  );

  private final LedgerIntegrityRunRepository runRepository;
  private final LedgerIntegrityAnomalyRecordRepository anomalyRepository;
  private final LedgerReconciliationCaseRepository caseRepository;
  private final LedgerIntegrityObservabilityProperties properties;
  private final Clock clock;
  private final TransactionOperations readOnlyTransactions;
  private final AtomicLong latestRunPassedGauge;
  private final AtomicLong unresolvedBacklogGauge;
  private final AtomicLong repairPendingGauge;
  private final AtomicLong criticalAnomalyGauge;
  private final AtomicLong lastCheckedAtEpochMillis;
  private final Map<String, Counter> alertCounters;

  @Autowired
  public LedgerIntegrityObservabilityService(
      LedgerIntegrityRunRepository runRepository,
      LedgerIntegrityAnomalyRecordRepository anomalyRepository,
      LedgerReconciliationCaseRepository caseRepository,
      MeterRegistry meterRegistry,
      LedgerIntegrityObservabilityProperties properties,
      PlatformTransactionManager transactionManager
  ) {
    this(
        runRepository,
        anomalyRepository,
        caseRepository,
        meterRegistry,
        properties,
        Clock.systemUTC(),
        newReadOnlyTransactions(transactionManager)
    );
  }

  LedgerIntegrityObservabilityService(
      LedgerIntegrityRunRepository runRepository,
      LedgerIntegrityAnomalyRecordRepository anomalyRepository,
      LedgerReconciliationCaseRepository caseRepository,
      MeterRegistry meterRegistry,
      LedgerIntegrityObservabilityProperties properties,
      Clock clock
  ) {
    this(
        runRepository,
        anomalyRepository,
        caseRepository,
        meterRegistry,
        properties,
        clock,
        immediateTransactions()
    );
  }

  private LedgerIntegrityObservabilityService(
      LedgerIntegrityRunRepository runRepository,
      LedgerIntegrityAnomalyRecordRepository anomalyRepository,
      LedgerReconciliationCaseRepository caseRepository,
      MeterRegistry meterRegistry,
      LedgerIntegrityObservabilityProperties properties,
      Clock clock,
      TransactionOperations readOnlyTransactions
  ) {
    this.runRepository = runRepository;
    this.anomalyRepository = anomalyRepository;
    this.caseRepository = caseRepository;
    this.properties = properties;
    this.clock = clock;
    this.readOnlyTransactions = readOnlyTransactions;
    this.latestRunPassedGauge = meterRegistry.gauge("corebank.ledger.integrity.run.passed", new AtomicLong(-1L));
    this.unresolvedBacklogGauge = meterRegistry.gauge("corebank.ledger.integrity.backlog.unresolved", new AtomicLong());
    this.repairPendingGauge = meterRegistry.gauge("corebank.ledger.integrity.backlog.repair_pending", new AtomicLong());
    this.criticalAnomalyGauge = meterRegistry.gauge("corebank.ledger.integrity.backlog.critical", new AtomicLong());
    this.lastCheckedAtEpochMillis = new AtomicLong(-1L);
    Gauge.builder("corebank.ledger.integrity.run.stale", lastCheckedAtEpochMillis, this::staleGaugeValue)
        .description("Whether the last stored ledger integrity run is stale")
        .register(meterRegistry);
    this.alertCounters = new ConcurrentHashMap<>();
    registerAlertCounter(meterRegistry, "UNRESOLVED_BACKLOG");
    registerAlertCounter(meterRegistry, "REPAIR_PENDING_BACKLOG");
    registerAlertCounter(meterRegistry, "CRITICAL_ANOMALY_BACKLOG");
    registerAlertCounter(meterRegistry, "STALE_LAST_RUN");
  }

  @PostConstruct
  void initialize() {
    try {
      updateMetrics(loadContext().summary());
    } catch (RuntimeException ex) {
      if (!isMissingLedgerIntegritySchema(ex)) {
        throw ex;
      }
      log.info("Skipping ledger integrity observability initialization because ledger integrity tables are unavailable");
      updateMetrics(emptySummary());
    }
  }

  public LedgerIntegrityObservabilitySummary readSummary() {
    return loadContext().summary();
  }

  public LedgerIntegrityObservabilityUpdate refreshMetricsAndEvaluateAlerts() {
    LedgerIntegrityObservabilityContext context = loadContext();
    updateMetrics(context.summary());
    List<LedgerIntegrityAlert> alerts = evaluateAlerts(context);
    emitAlerts(alerts);
    return new LedgerIntegrityObservabilityUpdate(context.summary(), List.copyOf(alerts));
  }

  public void refreshMetricsAndEvaluateAlertsAfterCommit() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      refreshMetricsAndEvaluateAlerts();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        refreshMetricsAndEvaluateAlerts();
      }
    });
  }

  private LedgerIntegrityObservabilityContext loadContext() {
    return Objects.requireNonNull(
        readOnlyTransactions.execute(status -> buildContext()),
        "ledger integrity observability context"
    );
  }

  private LedgerIntegrityObservabilitySummary emptySummary() {
    return LedgerIntegrityObservabilitySummary.of(
        null,
        null,
        null,
        null,
        null,
        0L,
        0L,
        0L,
        true,
        null,
        List.of()
    );
  }

  private LedgerIntegrityObservabilityContext buildContext() {
    Optional<LedgerIntegrityRun> latestRun = runRepository.findFirstByOrderByIdDesc();
    Optional<LedgerIntegrityRun> latestFailedRun = runRepository.findFirstByPassedFalseOrderByIdDesc();
    Long latestFailedRunId = latestFailedRun.map(LedgerIntegrityRun::getId).orElse(null);

    long openCaseBacklog = caseRepository.countByStatusIn(OPEN_CASE_STATUSES);
    long repairPendingCount = caseRepository.countByStatus(LedgerReconciliationCaseStatus.REPAIR_PENDING);
    long criticalOpenCaseBacklog = caseRepository.countByStatusInAndAnomalyTypeIn(OPEN_CASE_STATUSES, CRITICAL_ANOMALY_TYPES);

    List<LedgerReconciliationCase> openCaseSamples = caseRepository.findByStatusInOrderByRunIdDescIdAsc(
        OPEN_CASE_STATUSES,
        PageRequest.of(0, MAX_ALERT_IDENTIFIERS)
    );
    List<LedgerReconciliationCase> repairPendingSamples = caseRepository.findByStatusInOrderByRunIdDescIdAsc(
        REPAIR_PENDING_STATUSES,
        PageRequest.of(0, MAX_ALERT_IDENTIFIERS)
    );
    List<LedgerReconciliationCase> criticalOpenCaseSamples = caseRepository.findByStatusInAndAnomalyTypeInOrderByRunIdDescIdAsc(
        OPEN_CASE_STATUSES,
        CRITICAL_ANOMALY_TYPES,
        PageRequest.of(0, MAX_ALERT_IDENTIFIERS)
    );
    int openCaseRunCount = openCaseSamples.isEmpty() ? 0 : (int) caseRepository.countDistinctRunIdByStatusIn(OPEN_CASE_STATUSES);
    int repairPendingRunCount = repairPendingSamples.isEmpty()
        ? 0
        : (int) caseRepository.countDistinctRunIdByStatusIn(REPAIR_PENDING_STATUSES);
    int criticalOpenCaseRunCount = criticalOpenCaseSamples.isEmpty()
        ? 0
        : (int) caseRepository.countDistinctRunIdByStatusInAndAnomalyTypeIn(OPEN_CASE_STATUSES, CRITICAL_ANOMALY_TYPES);

    List<LedgerIntegrityFailedIdentifier> latestFailedIdentifiers = latestFailedRunId == null
        ? List.of()
        : anomalyRepository.findByRunIdOrderByIdAsc(latestFailedRunId, PageRequest.of(0, MAX_SUMMARY_IDENTIFIERS)).stream()
            .map(LedgerIntegrityFailedIdentifier::from)
            .toList();
    long latestFailedUntrackedCount = latestFailedRunId == null ? 0L : anomalyRepository.countUntrackedByRunId(latestFailedRunId);
    long latestFailedUntrackedCriticalCount = latestFailedRunId == null
        ? 0L
        : anomalyRepository.countUntrackedByRunIdAndTypeIn(latestFailedRunId, CRITICAL_ANOMALY_TYPES);
    List<LedgerIntegrityAnomalyRecord> latestFailedUntrackedSamples = latestFailedRunId == null
        ? List.of()
        : anomalyRepository.findUntrackedByRunIdOrderByIdAsc(latestFailedRunId, PageRequest.of(0, MAX_ALERT_IDENTIFIERS));
    List<LedgerIntegrityAnomalyRecord> latestFailedUntrackedCriticalSamples = latestFailedRunId == null
        ? List.of()
        : anomalyRepository.findUntrackedByRunIdAndTypeInOrderByIdAsc(
            latestFailedRunId,
            CRITICAL_ANOMALY_TYPES,
            PageRequest.of(0, MAX_ALERT_IDENTIFIERS)
        );

    Instant latestRunCheckedAt = latestRun.map(LedgerIntegrityRun::getCheckedAt).orElse(null);

    LedgerIntegrityObservabilitySummary summary = LedgerIntegrityObservabilitySummary.of(
        latestRun.map(LedgerIntegrityRun::getId).orElse(null),
        latestRunCheckedAt,
        latestRun.map(LedgerIntegrityRun::isPassed).orElse(null),
        latestRun.map(LedgerIntegrityRun::getAnomalyCount).orElse(null),
        latestRun.map(LedgerIntegrityRun::getSummaryMessage).orElse(null),
        openCaseBacklog + latestFailedUntrackedCount,
        repairPendingCount,
        criticalOpenCaseBacklog + latestFailedUntrackedCriticalCount,
        isStale(latestRunCheckedAt),
        latestFailedRunId,
        latestFailedIdentifiers
    );

    return new LedgerIntegrityObservabilityContext(
        summary,
        openCaseSamples,
        openCaseRunCount,
        repairPendingSamples,
        repairPendingRunCount,
        criticalOpenCaseSamples,
        criticalOpenCaseRunCount,
        latestFailedUntrackedSamples,
        latestFailedUntrackedCount > 0 ? 1 : 0,
        latestFailedUntrackedCriticalSamples,
        latestFailedUntrackedCriticalCount > 0 ? 1 : 0
    );
  }

  private void updateMetrics(LedgerIntegrityObservabilitySummary summary) {
    latestRunPassedGauge.set(
        summary.getLatestRunPassed() == null ? -1L : (Boolean.TRUE.equals(summary.getLatestRunPassed()) ? 1L : 0L)
    );
    unresolvedBacklogGauge.set(summary.getUnresolvedAnomalyCount());
    repairPendingGauge.set(summary.getRepairPendingCount());
    criticalAnomalyGauge.set(summary.getCriticalAnomalyCount());
    lastCheckedAtEpochMillis.set(
        summary.getLatestRunCheckedAt() == null ? -1L : summary.getLatestRunCheckedAt().toEpochMilli()
    );
  }

  private List<LedgerIntegrityAlert> evaluateAlerts(LedgerIntegrityObservabilityContext context) {
    LedgerIntegrityObservabilitySummary summary = context.summary();
    List<LedgerIntegrityAlert> alerts = new ArrayList<>();
    LedgerIntegrityObservabilityProperties.Alert alert = properties.getAlert();

    addThresholdAlert(
        alerts,
        "UNRESOLVED_BACKLOG",
        "warn",
        alert.getUnresolvedBacklogThreshold(),
        summary.getUnresolvedAnomalyCount(),
        selectAlertContext(
            context.latestFailedUntrackedSamples(),
            context.latestFailedUntrackedRunCount(),
            context.openCaseSamples(),
            context.openCaseRunCount()
        ),
        summary
    );
    addThresholdAlert(
        alerts,
        "REPAIR_PENDING_BACKLOG",
        "warn",
        alert.getRepairPendingThreshold(),
        summary.getRepairPendingCount(),
        selectAlertContext(List.of(), 0, context.repairPendingSamples(), context.repairPendingRunCount()),
        summary
    );
    addThresholdAlert(
        alerts,
        "CRITICAL_ANOMALY_BACKLOG",
        "critical",
        alert.getCriticalAnomalyThreshold(),
        summary.getCriticalAnomalyCount(),
        selectAlertContext(
            context.latestFailedUntrackedCriticalSamples(),
            context.latestFailedUntrackedCriticalRunCount(),
            context.criticalOpenCaseSamples(),
            context.criticalOpenCaseRunCount()
        ),
        summary
    );

    if (summary.getLatestRunId() != null && summary.isStaleLastRun()) {
      LedgerIntegrityAlertContext staleAlertContext = selectAlertContext(
          context.latestFailedUntrackedSamples(),
          context.latestFailedUntrackedRunCount(),
          context.openCaseSamples(),
          context.openCaseRunCount()
      );
      alerts.add(new LedgerIntegrityAlert(
          "STALE_LAST_RUN",
          "critical",
          summary.getLatestRunId(),
          staleAlertContext.runId(),
          staleAlertContext.runCount(),
          summary.getUnresolvedAnomalyCount(),
          summary.getRepairPendingCount(),
          summary.getCriticalAnomalyCount(),
          true,
          staleAlertContext.identifiers()
      ));
    }

    return alerts;
  }

  private void addThresholdAlert(
      List<LedgerIntegrityAlert> alerts,
      String type,
      String severity,
      long threshold,
      long actual,
      LedgerIntegrityAlertContext alertContext,
      LedgerIntegrityObservabilitySummary summary
  ) {
    if (threshold <= 0 || actual < threshold) {
      return;
    }
    alerts.add(new LedgerIntegrityAlert(
        type,
        severity,
        alertContext.runId(),
        alertContext.runId(),
        alertContext.runCount(),
        summary.getUnresolvedAnomalyCount(),
        summary.getRepairPendingCount(),
        summary.getCriticalAnomalyCount(),
        summary.isStaleLastRun(),
        alertContext.identifiers()
    ));
  }

  private LedgerIntegrityAlertContext selectAlertContext(
      List<LedgerIntegrityAnomalyRecord> preferredAnomalies,
      int preferredRunCount,
      List<LedgerReconciliationCase> fallbackCases,
      int fallbackRunCount
  ) {
    if (!preferredAnomalies.isEmpty()) {
      Long runId = preferredAnomalies.get(0).getRunId();
      return new LedgerIntegrityAlertContext(
          runId,
          preferredRunCount,
          preferredAnomalies.stream()
              .limit(MAX_ALERT_IDENTIFIERS)
              .map(LedgerIntegrityFailedIdentifier::from)
              .toList()
      );
    }
    if (!fallbackCases.isEmpty()) {
      Long runId = fallbackCases.get(0).getRunId();
      return new LedgerIntegrityAlertContext(
          runId,
          fallbackRunCount,
          fallbackCases.stream()
              .filter(reconciliationCase -> Objects.equals(reconciliationCase.getRunId(), runId))
              .limit(MAX_ALERT_IDENTIFIERS)
              .map(LedgerIntegrityFailedIdentifier::from)
              .toList()
      );
    }
    return new LedgerIntegrityAlertContext(null, 0, List.of());
  }

  private void emitAlerts(List<LedgerIntegrityAlert> alerts) {
    for (LedgerIntegrityAlert alert : alerts) {
      Counter counter = alertCounters.get(alert.type());
      if (counter != null) {
        counter.increment();
      }
      log.warn(
          "ledger_integrity_alert type={} severity={} runId={} sampleRunId={} contributingRunCount={} unresolvedAnomalyCount={} repairPendingCount={} criticalAnomalyCount={} staleLastRun={} identifierCount={} identifiers={}",
          alert.type(),
          alert.severity(),
          alert.runId(),
          alert.sampleRunId(),
          alert.contributingRunCount(),
          alert.unresolvedAnomalyCount(),
          alert.repairPendingCount(),
          alert.criticalAnomalyCount(),
          alert.staleLastRun(),
          alert.identifiers().size(),
          formatIdentifiers(alert.identifiers())
      );
    }
  }

  private void registerAlertCounter(MeterRegistry meterRegistry, String type) {
    alertCounters.put(
        type,
        Counter.builder("corebank.ledger.integrity.alerts")
            .description("Structured ledger integrity alerts emitted by the observability layer")
            .tag("type", type)
            .register(meterRegistry)
    );
  }

  private double staleGaugeValue(AtomicLong lastCheckedAtMillis) {
    return isStaleEpochMillis(lastCheckedAtMillis.get()) ? 1.0d : 0.0d;
  }

  private boolean isStale(Instant lastCheckedAt) {
    return isStaleEpochMillis(lastCheckedAt == null ? -1L : lastCheckedAt.toEpochMilli());
  }

  private boolean isStaleEpochMillis(long lastCheckedAtMillis) {
    if (lastCheckedAtMillis < 0) {
      return true;
    }

    Duration staleAfter = properties.getStaleAfter() == null ? Duration.ofMinutes(5) : properties.getStaleAfter();
    Instant lastCheckedAt = Instant.ofEpochMilli(lastCheckedAtMillis);
    return Duration.between(lastCheckedAt, Instant.now(clock)).compareTo(staleAfter) >= 0;
  }

  private List<String> formatIdentifiers(List<LedgerIntegrityFailedIdentifier> identifiers) {
    return identifiers.stream()
        .map(identifier -> "anomalyId=%s,type=%s,accountId=%s,symbol=%s,positionId=%s,executionId=%s,orderId=%s,clOrdId=%s,journalEntryId=%s,ledgerEntryId=%s"
            .formatted(
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
            ))
        .toList();
  }

  private boolean isMissingLedgerIntegritySchema(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      String message = current.getMessage();
      if (message != null
          && (message.contains("doesn't exist")
              || message.contains("does not exist")
              || message.contains("no such table"))
          && LEDGER_INTEGRITY_TABLE_NAMES.stream().anyMatch(message::contains)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static TransactionOperations newReadOnlyTransactions(PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setReadOnly(true);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return transactionTemplate;
  }

  private static TransactionOperations immediateTransactions() {
    return new TransactionOperations() {
      @Override
      public <T> T execute(TransactionCallback<T> action) {
        return action.doInTransaction(new SimpleTransactionStatus());
      }
    };
  }

  public record LedgerIntegrityObservabilityUpdate(
      LedgerIntegrityObservabilitySummary summary,
      List<LedgerIntegrityAlert> alerts
  ) {
  }

  private record LedgerIntegrityObservabilityContext(
      LedgerIntegrityObservabilitySummary summary,
      List<LedgerReconciliationCase> openCaseSamples,
      int openCaseRunCount,
      List<LedgerReconciliationCase> repairPendingSamples,
      int repairPendingRunCount,
      List<LedgerReconciliationCase> criticalOpenCaseSamples,
      int criticalOpenCaseRunCount,
      List<LedgerIntegrityAnomalyRecord> latestFailedUntrackedSamples,
      int latestFailedUntrackedRunCount,
      List<LedgerIntegrityAnomalyRecord> latestFailedUntrackedCriticalSamples,
      int latestFailedUntrackedCriticalRunCount
  ) {
  }

  private record LedgerIntegrityAlertContext(
      Long runId,
      int runCount,
      List<LedgerIntegrityFailedIdentifier> identifiers
  ) {
  }

  public record LedgerIntegrityAlert(
      String type,
      String severity,
      Long runId,
      Long sampleRunId,
      int contributingRunCount,
      long unresolvedAnomalyCount,
      long repairPendingCount,
      long criticalAnomalyCount,
      boolean staleLastRun,
      List<LedgerIntegrityFailedIdentifier> identifiers
  ) {
  }
}
