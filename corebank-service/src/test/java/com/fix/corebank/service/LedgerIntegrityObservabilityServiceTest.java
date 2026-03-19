package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fix.corebank.config.LedgerIntegrityObservabilityProperties;
import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import com.fix.corebank.entity.LedgerIntegrityRun;
import com.fix.corebank.entity.LedgerReconciliationCase;
import com.fix.corebank.entity.LedgerReconciliationCaseStatus;
import com.fix.corebank.repository.LedgerIntegrityAnomalyRecordRepository;
import com.fix.corebank.repository.LedgerIntegrityRunRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseRepository;
import com.fix.corebank.service.LedgerIntegrityObservabilityService.LedgerIntegrityAlert;
import com.fix.corebank.service.LedgerIntegrityObservabilityService.LedgerIntegrityObservabilityUpdate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LedgerIntegrityObservabilityServiceTest {

  @Mock
  private LedgerIntegrityRunRepository runRepository;

  @Mock
  private LedgerIntegrityAnomalyRecordRepository anomalyRepository;

  @Mock
  private LedgerReconciliationCaseRepository caseRepository;

  private SimpleMeterRegistry meterRegistry;
  private LedgerIntegrityObservabilityProperties properties;
  private LedgerIntegrityObservabilityService service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    properties = new LedgerIntegrityObservabilityProperties();
    properties.setStaleAfter(Duration.ofMinutes(5));
    properties.getAlert().setUnresolvedBacklogThreshold(1L);
    properties.getAlert().setRepairPendingThreshold(1L);
    properties.getAlert().setCriticalAnomalyThreshold(1L);
    Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
    service = new LedgerIntegrityObservabilityService(
        runRepository,
        anomalyRepository,
        caseRepository,
        meterRegistry,
        properties,
        clock
    );
  }

  @Test
  void shouldRefreshMetricsAndEmitStructuredAlertsWhenThresholdsAreBreached(CapturedOutput output) {
    LedgerIntegrityRun failedRun = run(
        41L,
        Instant.parse("2026-03-01T09:50:00Z"),
        false,
        2,
        "NEGATIVE_POSITION: negative position (+1 more)"
    );
    LedgerIntegrityAnomalyRecord critical = anomaly(
        9001L,
        41L,
        "NEGATIVE_POSITION",
        1L,
        "005930",
        11L,
        null,
        101L,
        "123e4567-e89b-42d3-a456-426614174201",
        201L,
        301L
    );
    LedgerIntegrityAnomalyRecord nonCritical = anomaly(
        9002L,
        41L,
        "MISSING_LEDGER_CL_ORD_REF",
        1L,
        "005930",
        11L,
        null,
        101L,
        "123e4567-e89b-42d3-a456-426614174202",
        202L,
        302L
    );
    LedgerReconciliationCase repairPendingCase = repairPendingCase(
        critical,
        Instant.parse("2026-03-01T09:51:00Z")
    );

    when(runRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(failedRun));
    when(runRepository.findFirstByPassedFalseOrderByIdDesc()).thenReturn(Optional.of(failedRun));
    when(caseRepository.countByStatusIn(anyCollection())).thenReturn(1L);
    when(caseRepository.countByStatus(LedgerReconciliationCaseStatus.REPAIR_PENDING)).thenReturn(1L);
    when(caseRepository.countByStatusInAndAnomalyTypeIn(anyCollection(), anyCollection())).thenReturn(1L);
    when(caseRepository.findByStatusInOrderByRunIdDescIdAsc(anyCollection(), any())).thenReturn(List.of(repairPendingCase));
    when(caseRepository.findByStatusInAndAnomalyTypeInOrderByRunIdDescIdAsc(anyCollection(), anyCollection(), any()))
        .thenReturn(List.of(repairPendingCase));
    when(caseRepository.countDistinctRunIdByStatusIn(anyCollection())).thenReturn(1L);
    when(caseRepository.countDistinctRunIdByStatusInAndAnomalyTypeIn(anyCollection(), anyCollection())).thenReturn(1L);
    when(anomalyRepository.findByRunIdOrderByIdAsc(eq(41L), any())).thenReturn(List.of(critical, nonCritical));
    when(anomalyRepository.countUntrackedByRunId(41L)).thenReturn(1L);
    when(anomalyRepository.countUntrackedByRunIdAndTypeIn(eq(41L), anyCollection())).thenReturn(0L);
    when(anomalyRepository.findUntrackedByRunIdOrderByIdAsc(eq(41L), any())).thenReturn(List.of(nonCritical));
    when(anomalyRepository.findUntrackedByRunIdAndTypeInOrderByIdAsc(eq(41L), anyCollection(), any()))
        .thenReturn(List.of());

    LedgerIntegrityObservabilityUpdate update = service.refreshMetricsAndEvaluateAlerts();

    assertThat(update.summary().getLatestRunId()).isEqualTo(41L);
    assertThat(update.summary().getLatestRunPassed()).isFalse();
    assertThat(update.summary().getUnresolvedAnomalyCount()).isEqualTo(2L);
    assertThat(update.summary().getRepairPendingCount()).isEqualTo(1L);
    assertThat(update.summary().getCriticalAnomalyCount()).isEqualTo(1L);
    assertThat(update.summary().isStaleLastRun()).isTrue();
    assertThat(update.alerts()).extracting(LedgerIntegrityAlert::type)
        .containsExactlyInAnyOrder(
            "UNRESOLVED_BACKLOG",
            "REPAIR_PENDING_BACKLOG",
            "CRITICAL_ANOMALY_BACKLOG",
            "STALE_LAST_RUN"
        );

    assertThat(meterRegistry.get("corebank.ledger.integrity.run.passed").gauge().value()).isEqualTo(0.0d);
    assertThat(meterRegistry.get("corebank.ledger.integrity.backlog.unresolved").gauge().value()).isEqualTo(2.0d);
    assertThat(meterRegistry.get("corebank.ledger.integrity.backlog.repair_pending").gauge().value()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("corebank.ledger.integrity.backlog.critical").gauge().value()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("corebank.ledger.integrity.run.stale").gauge().value()).isEqualTo(1.0d);
    assertThat(alertCounter("UNRESOLVED_BACKLOG")).isEqualTo(1.0d);
    assertThat(alertCounter("REPAIR_PENDING_BACKLOG")).isEqualTo(1.0d);
    assertThat(alertCounter("CRITICAL_ANOMALY_BACKLOG")).isEqualTo(1.0d);
    assertThat(alertCounter("STALE_LAST_RUN")).isEqualTo(1.0d);
    assertThat(output.getOut())
        .contains("ledger_integrity_alert type=UNRESOLVED_BACKLOG")
        .contains("ledger_integrity_alert type=STALE_LAST_RUN")
        .contains("runId=41")
        .contains("sampleRunId=41")
        .contains("anomalyId=9002,type=MISSING_LEDGER_CL_ORD_REF");
  }

  @Test
  void shouldKeepFreshPassingRunMetricsWithoutAlertsWhenBelowThresholds() {
    properties.getAlert().setUnresolvedBacklogThreshold(5L);
    properties.getAlert().setRepairPendingThreshold(3L);
    properties.getAlert().setCriticalAnomalyThreshold(2L);

    LedgerIntegrityRun passedRun = run(
        42L,
        Instant.parse("2026-03-01T09:59:00Z"),
        true,
        0,
        "Ledger integrity check passed"
    );

    when(runRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(passedRun));
    when(runRepository.findFirstByPassedFalseOrderByIdDesc()).thenReturn(Optional.empty());
    when(caseRepository.countByStatusIn(anyCollection())).thenReturn(0L);
    when(caseRepository.countByStatus(LedgerReconciliationCaseStatus.REPAIR_PENDING)).thenReturn(0L);
    when(caseRepository.countByStatusInAndAnomalyTypeIn(anyCollection(), anyCollection())).thenReturn(0L);
    when(caseRepository.findByStatusInOrderByRunIdDescIdAsc(anyCollection(), any())).thenReturn(List.of());
    when(caseRepository.findByStatusInAndAnomalyTypeInOrderByRunIdDescIdAsc(anyCollection(), anyCollection(), any()))
        .thenReturn(List.of());

    LedgerIntegrityObservabilityUpdate update = service.refreshMetricsAndEvaluateAlerts();

    assertThat(update.summary().getLatestRunId()).isEqualTo(42L);
    assertThat(update.summary().getLatestRunPassed()).isTrue();
    assertThat(update.summary().isStaleLastRun()).isFalse();
    assertThat(update.alerts()).isEmpty();
    assertThat(meterRegistry.get("corebank.ledger.integrity.run.passed").gauge().value()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("corebank.ledger.integrity.run.stale").gauge().value()).isEqualTo(0.0d);
    assertThat(alertCounter("UNRESOLVED_BACKLOG")).isEqualTo(0.0d);
    assertThat(alertCounter("REPAIR_PENDING_BACKLOG")).isEqualTo(0.0d);
    assertThat(alertCounter("CRITICAL_ANOMALY_BACKLOG")).isEqualTo(0.0d);
    assertThat(alertCounter("STALE_LAST_RUN")).isEqualTo(0.0d);
  }

  @Test
  void shouldExposeMissingRunWithSentinelPassedMetricAndNoAlerts() {
    when(runRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());
    when(runRepository.findFirstByPassedFalseOrderByIdDesc()).thenReturn(Optional.empty());
    when(caseRepository.countByStatusIn(anyCollection())).thenReturn(0L);
    when(caseRepository.countByStatus(LedgerReconciliationCaseStatus.REPAIR_PENDING)).thenReturn(0L);
    when(caseRepository.countByStatusInAndAnomalyTypeIn(anyCollection(), anyCollection())).thenReturn(0L);
    when(caseRepository.findByStatusInOrderByRunIdDescIdAsc(anyCollection(), any())).thenReturn(List.of());
    when(caseRepository.findByStatusInAndAnomalyTypeInOrderByRunIdDescIdAsc(anyCollection(), anyCollection(), any()))
        .thenReturn(List.of());

    LedgerIntegrityObservabilityUpdate update = service.refreshMetricsAndEvaluateAlerts();

    assertThat(update.summary().getLatestRunId()).isNull();
    assertThat(update.summary().getLatestRunPassed()).isNull();
    assertThat(update.summary().isStaleLastRun()).isTrue();
    assertThat(update.alerts()).isEmpty();
    assertThat(meterRegistry.get("corebank.ledger.integrity.run.passed").gauge().value()).isEqualTo(-1.0d);
    assertThat(meterRegistry.get("corebank.ledger.integrity.run.stale").gauge().value()).isEqualTo(1.0d);
  }

  @Test
  void shouldRefreshMetricsWithoutEmittingAlertsDuringInitialization(CapturedOutput output) {
    LedgerIntegrityRun failedRun = run(
        61L,
        Instant.parse("2026-03-01T09:50:00Z"),
        false,
        1,
        "NEGATIVE_POSITION: negative position"
    );
    LedgerIntegrityAnomalyRecord anomaly = anomaly(
        9401L,
        61L,
        "NEGATIVE_POSITION",
        1L,
        "005930",
        11L,
        null,
        101L,
        "123e4567-e89b-42d3-a456-426614174241",
        201L,
        301L
    );

    when(runRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(failedRun));
    when(runRepository.findFirstByPassedFalseOrderByIdDesc()).thenReturn(Optional.of(failedRun));
    when(caseRepository.countByStatusIn(anyCollection())).thenReturn(0L);
    when(caseRepository.countByStatus(LedgerReconciliationCaseStatus.REPAIR_PENDING)).thenReturn(0L);
    when(caseRepository.countByStatusInAndAnomalyTypeIn(anyCollection(), anyCollection())).thenReturn(0L);
    when(caseRepository.findByStatusInOrderByRunIdDescIdAsc(anyCollection(), any())).thenReturn(List.of());
    when(caseRepository.findByStatusInAndAnomalyTypeInOrderByRunIdDescIdAsc(anyCollection(), anyCollection(), any()))
        .thenReturn(List.of());
    when(anomalyRepository.findByRunIdOrderByIdAsc(eq(61L), any())).thenReturn(List.of(anomaly));
    when(anomalyRepository.countUntrackedByRunId(61L)).thenReturn(1L);
    when(anomalyRepository.countUntrackedByRunIdAndTypeIn(eq(61L), anyCollection())).thenReturn(1L);
    when(anomalyRepository.findUntrackedByRunIdOrderByIdAsc(eq(61L), any())).thenReturn(List.of(anomaly));
    when(anomalyRepository.findUntrackedByRunIdAndTypeInOrderByIdAsc(eq(61L), anyCollection(), any()))
        .thenReturn(List.of(anomaly));

    service.initialize();

    assertThat(meterRegistry.get("corebank.ledger.integrity.run.passed").gauge().value()).isEqualTo(0.0d);
    assertThat(meterRegistry.get("corebank.ledger.integrity.backlog.unresolved").gauge().value()).isEqualTo(1.0d);
    assertThat(alertCounter("UNRESOLVED_BACKLOG")).isEqualTo(0.0d);
    assertThat(alertCounter("STALE_LAST_RUN")).isEqualTo(0.0d);
    assertThat(output.getOut()).doesNotContain("ledger_integrity_alert");
  }

  @Test
  void shouldCapPreferredAlertIdentifierSamplesAtConfiguredMaximum() {
    LedgerIntegrityRun failedRun = run(
        51L,
        Instant.parse("2026-03-01T09:50:00Z"),
        false,
        12,
        "NEGATIVE_POSITION: negative position (+11 more)"
    );
    List<LedgerIntegrityAnomalyRecord> anomalies = new ArrayList<>();
    for (long anomalyId = 9301L; anomalyId <= 9312L; anomalyId++) {
      anomalies.add(anomaly(
          anomalyId,
          51L,
          "NEGATIVE_POSITION",
          1L,
          "005930",
          11L,
          null,
          100L + anomalyId,
          "123e4567-e89b-42d3-a456-42661417%04d".formatted(anomalyId),
          200L + anomalyId,
          300L + anomalyId
      ));
    }

    when(runRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(failedRun));
    when(runRepository.findFirstByPassedFalseOrderByIdDesc()).thenReturn(Optional.of(failedRun));
    when(caseRepository.countByStatusIn(anyCollection())).thenReturn(0L);
    when(caseRepository.countByStatus(LedgerReconciliationCaseStatus.REPAIR_PENDING)).thenReturn(0L);
    when(caseRepository.countByStatusInAndAnomalyTypeIn(anyCollection(), anyCollection())).thenReturn(0L);
    when(caseRepository.findByStatusInOrderByRunIdDescIdAsc(anyCollection(), any())).thenReturn(List.of());
    when(caseRepository.findByStatusInAndAnomalyTypeInOrderByRunIdDescIdAsc(anyCollection(), anyCollection(), any()))
        .thenReturn(List.of());
    when(anomalyRepository.findByRunIdOrderByIdAsc(eq(51L), any())).thenReturn(anomalies);
    when(anomalyRepository.countUntrackedByRunId(51L)).thenReturn(12L);
    when(anomalyRepository.countUntrackedByRunIdAndTypeIn(eq(51L), anyCollection())).thenReturn(12L);
    when(anomalyRepository.findUntrackedByRunIdOrderByIdAsc(eq(51L), any())).thenReturn(anomalies.subList(0, 10));
    when(anomalyRepository.findUntrackedByRunIdAndTypeInOrderByIdAsc(eq(51L), anyCollection(), any()))
        .thenReturn(anomalies.subList(0, 10));

    LedgerIntegrityObservabilityUpdate update = service.refreshMetricsAndEvaluateAlerts();
    LedgerIntegrityAlert unresolvedAlert = update.alerts().stream()
        .filter(alert -> "UNRESOLVED_BACKLOG".equals(alert.type()))
        .findFirst()
        .orElseThrow();

    assertThat(update.summary().getUnresolvedAnomalyCount()).isEqualTo(12L);
    assertThat(unresolvedAlert.runId()).isEqualTo(51L);
    assertThat(unresolvedAlert.sampleRunId()).isEqualTo(51L);
    assertThat(unresolvedAlert.contributingRunCount()).isEqualTo(1);
    assertThat(unresolvedAlert.identifiers()).hasSize(10);
    assertThat(unresolvedAlert.identifiers())
        .extracting(identifier -> identifier.getAnomalyId())
        .containsExactly(9301L, 9302L, 9303L, 9304L, 9305L, 9306L, 9307L, 9308L, 9309L, 9310L);
  }

  @Test
  void shouldExcludeTerminalCasesFromUnresolvedBacklog() {
    LedgerIntegrityRun latestPassedRun = run(
        43L,
        Instant.parse("2026-03-01T09:59:00Z"),
        true,
        0,
        "Ledger integrity check passed"
    );
    LedgerIntegrityRun latestFailedRun = run(
        42L,
        Instant.parse("2026-03-01T09:50:00Z"),
        false,
        1,
        "NEGATIVE_POSITION: negative position"
    );
    LedgerIntegrityAnomalyRecord resolvedAnomaly = anomaly(
        9101L,
        42L,
        "NEGATIVE_POSITION",
        1L,
        "005930",
        11L,
        null,
        101L,
        "123e4567-e89b-42d3-a456-426614174211",
        201L,
        301L
    );
    LedgerReconciliationCase waivedCase = waivedCase(
        resolvedAnomaly,
        Instant.parse("2026-03-01T09:55:00Z")
    );

    when(runRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(latestPassedRun));
    when(runRepository.findFirstByPassedFalseOrderByIdDesc()).thenReturn(Optional.of(latestFailedRun));
    when(caseRepository.countByStatusIn(anyCollection())).thenReturn(0L);
    when(caseRepository.countByStatus(LedgerReconciliationCaseStatus.REPAIR_PENDING)).thenReturn(0L);
    when(caseRepository.countByStatusInAndAnomalyTypeIn(anyCollection(), anyCollection())).thenReturn(0L);
    when(caseRepository.findByStatusInOrderByRunIdDescIdAsc(anyCollection(), any())).thenReturn(List.of());
    when(caseRepository.findByStatusInAndAnomalyTypeInOrderByRunIdDescIdAsc(anyCollection(), anyCollection(), any()))
        .thenReturn(List.of());
    when(anomalyRepository.findByRunIdOrderByIdAsc(eq(42L), any())).thenReturn(List.of(resolvedAnomaly));
    when(anomalyRepository.countUntrackedByRunId(42L)).thenReturn(0L);
    when(anomalyRepository.countUntrackedByRunIdAndTypeIn(eq(42L), anyCollection())).thenReturn(0L);
    when(anomalyRepository.findUntrackedByRunIdOrderByIdAsc(eq(42L), any())).thenReturn(List.of());
    when(anomalyRepository.findUntrackedByRunIdAndTypeInOrderByIdAsc(eq(42L), anyCollection(), any())).thenReturn(List.of());

    LedgerIntegrityObservabilityUpdate update = service.refreshMetricsAndEvaluateAlerts();

    assertThat(update.summary().getUnresolvedAnomalyCount()).isZero();
    assertThat(update.summary().getCriticalAnomalyCount()).isZero();
    assertThat(update.alerts()).isEmpty();
  }

  @Test
  void shouldUseOpenCaseRunForBacklogAlertWhenLatestFailedRunIsAlreadyResolved() {
    LedgerIntegrityRun latestPassedRun = run(
        43L,
        Instant.parse("2026-03-01T09:59:00Z"),
        true,
        0,
        "Ledger integrity check passed"
    );
    LedgerIntegrityRun latestFailedRun = run(
        42L,
        Instant.parse("2026-03-01T09:52:00Z"),
        false,
        1,
        "MISSING_LEDGER_CL_ORD_REF: missing ref"
    );
    LedgerIntegrityAnomalyRecord resolvedLatestFailedAnomaly = anomaly(
        9201L,
        42L,
        "MISSING_LEDGER_CL_ORD_REF",
        1L,
        "005930",
        11L,
        null,
        102L,
        "123e4567-e89b-42d3-a456-426614174221",
        202L,
        302L
    );
    LedgerReconciliationCase waivedLatestFailedCase = waivedCase(
        resolvedLatestFailedAnomaly,
        Instant.parse("2026-03-01T09:54:00Z")
    );
    LedgerIntegrityAnomalyRecord olderOpenAnomaly = anomaly(
        9202L,
        41L,
        "MISSING_LEDGER_CL_ORD_REF",
        1L,
        "000660",
        12L,
        null,
        103L,
        "123e4567-e89b-42d3-a456-426614174222",
        203L,
        303L
    );
    LedgerReconciliationCase acknowledgedCase = acknowledgedCase(
        olderOpenAnomaly,
        Instant.parse("2026-03-01T09:40:00Z")
    );
    LedgerIntegrityAnomalyRecord evenOlderOpenAnomaly = anomaly(
        9203L,
        40L,
        "MISSING_LEDGER_CL_ORD_REF",
        1L,
        "035420",
        13L,
        null,
        104L,
        "123e4567-e89b-42d3-a456-426614174223",
        204L,
        304L
    );
    LedgerReconciliationCase reopenedCase = acknowledgedCase(
        evenOlderOpenAnomaly,
        Instant.parse("2026-03-01T09:39:00Z")
    );

    when(runRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(latestPassedRun));
    when(runRepository.findFirstByPassedFalseOrderByIdDesc()).thenReturn(Optional.of(latestFailedRun));
    when(caseRepository.countByStatusIn(anyCollection())).thenReturn(2L);
    when(caseRepository.countByStatus(LedgerReconciliationCaseStatus.REPAIR_PENDING)).thenReturn(0L);
    when(caseRepository.countByStatusInAndAnomalyTypeIn(anyCollection(), anyCollection())).thenReturn(0L);
    when(caseRepository.findByStatusInOrderByRunIdDescIdAsc(anyCollection(), any())).thenReturn(List.of(acknowledgedCase, reopenedCase));
    when(caseRepository.findByStatusInAndAnomalyTypeInOrderByRunIdDescIdAsc(anyCollection(), anyCollection(), any()))
        .thenReturn(List.of());
    when(caseRepository.countDistinctRunIdByStatusIn(anyCollection())).thenReturn(2L);
    when(anomalyRepository.findByRunIdOrderByIdAsc(eq(42L), any())).thenReturn(List.of(resolvedLatestFailedAnomaly));
    when(anomalyRepository.countUntrackedByRunId(42L)).thenReturn(0L);
    when(anomalyRepository.countUntrackedByRunIdAndTypeIn(eq(42L), anyCollection())).thenReturn(0L);
    when(anomalyRepository.findUntrackedByRunIdOrderByIdAsc(eq(42L), any())).thenReturn(List.of());
    when(anomalyRepository.findUntrackedByRunIdAndTypeInOrderByIdAsc(eq(42L), anyCollection(), any())).thenReturn(List.of());

    LedgerIntegrityObservabilityUpdate update = service.refreshMetricsAndEvaluateAlerts();
    LedgerIntegrityAlert unresolvedAlert = update.alerts().stream()
        .filter(alert -> "UNRESOLVED_BACKLOG".equals(alert.type()))
        .findFirst()
        .orElseThrow();

    assertThat(update.summary().getUnresolvedAnomalyCount()).isEqualTo(2L);
    assertThat(unresolvedAlert.runId()).isEqualTo(41L);
    assertThat(unresolvedAlert.sampleRunId()).isEqualTo(41L);
    assertThat(unresolvedAlert.contributingRunCount()).isEqualTo(2);
    assertThat(unresolvedAlert.identifiers()).singleElement().satisfies(identifier -> {
      assertThat(identifier.getAnomalyId()).isEqualTo(9202L);
      assertThat(identifier.getClOrdId()).isEqualTo("123e4567-e89b-42d3-a456-426614174222");
    });
  }

  @Test
  void shouldIncludeBacklogSampleInStaleAlertWhenLatestRunPassed() {
    LedgerIntegrityRun latestPassedRun = run(
        73L,
        Instant.parse("2026-03-01T09:00:00Z"),
        true,
        0,
        "Ledger integrity check passed"
    );
    LedgerIntegrityRun latestFailedRun = run(
        72L,
        Instant.parse("2026-03-01T08:55:00Z"),
        false,
        1,
        "MISSING_LEDGER_CL_ORD_REF: missing ref"
    );
    LedgerIntegrityAnomalyRecord olderOpenAnomaly = anomaly(
        9501L,
        72L,
        "MISSING_LEDGER_CL_ORD_REF",
        1L,
        "005930",
        11L,
        null,
        101L,
        "123e4567-e89b-42d3-a456-426614174251",
        201L,
        301L
    );
    LedgerReconciliationCase acknowledgedCase = acknowledgedCase(
        olderOpenAnomaly,
        Instant.parse("2026-03-01T08:56:00Z")
    );

    when(runRepository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(latestPassedRun));
    when(runRepository.findFirstByPassedFalseOrderByIdDesc()).thenReturn(Optional.of(latestFailedRun));
    when(caseRepository.countByStatusIn(anyCollection())).thenReturn(1L);
    when(caseRepository.countByStatus(LedgerReconciliationCaseStatus.REPAIR_PENDING)).thenReturn(0L);
    when(caseRepository.countByStatusInAndAnomalyTypeIn(anyCollection(), anyCollection())).thenReturn(0L);
    when(caseRepository.findByStatusInOrderByRunIdDescIdAsc(anyCollection(), any())).thenReturn(List.of(acknowledgedCase));
    when(caseRepository.findByStatusInAndAnomalyTypeInOrderByRunIdDescIdAsc(anyCollection(), anyCollection(), any()))
        .thenReturn(List.of());
    when(caseRepository.countDistinctRunIdByStatusIn(anyCollection())).thenReturn(1L);
    when(anomalyRepository.findByRunIdOrderByIdAsc(eq(72L), any())).thenReturn(List.of(olderOpenAnomaly));
    when(anomalyRepository.countUntrackedByRunId(72L)).thenReturn(0L);
    when(anomalyRepository.countUntrackedByRunIdAndTypeIn(eq(72L), anyCollection())).thenReturn(0L);
    when(anomalyRepository.findUntrackedByRunIdOrderByIdAsc(eq(72L), any())).thenReturn(List.of());
    when(anomalyRepository.findUntrackedByRunIdAndTypeInOrderByIdAsc(eq(72L), anyCollection(), any())).thenReturn(List.of());

    LedgerIntegrityObservabilityUpdate update = service.refreshMetricsAndEvaluateAlerts();
    LedgerIntegrityAlert staleAlert = update.alerts().stream()
        .filter(alert -> "STALE_LAST_RUN".equals(alert.type()))
        .findFirst()
        .orElseThrow();

    assertThat(staleAlert.runId()).isEqualTo(73L);
    assertThat(staleAlert.sampleRunId()).isEqualTo(72L);
    assertThat(staleAlert.contributingRunCount()).isEqualTo(1);
    assertThat(staleAlert.identifiers()).singleElement().satisfies(identifier -> {
      assertThat(identifier.getAnomalyId()).isEqualTo(9501L);
      assertThat(identifier.getClOrdId()).isEqualTo("123e4567-e89b-42d3-a456-426614174251");
    });
  }

  private double alertCounter(String type) {
    return meterRegistry.get("corebank.ledger.integrity.alerts")
        .tag("type", type)
        .counter()
        .count();
  }

  private LedgerIntegrityRun run(Long id, Instant checkedAt, boolean passed, int anomalyCount, String summaryMessage) {
    LedgerIntegrityRun run = LedgerIntegrityRun.of(checkedAt, passed, anomalyCount, summaryMessage);
    ReflectionTestUtils.setField(run, "id", id);
    return run;
  }

  private LedgerIntegrityAnomalyRecord anomaly(
      Long id,
      Long runId,
      String type,
      Long accountId,
      String symbol,
      Long positionId,
      Long executionId,
      Long orderId,
      String clOrdId,
      Long journalEntryId,
      Long ledgerEntryId
  ) {
    LedgerIntegrityAnomalyRecord anomaly = LedgerIntegrityAnomalyRecord.of(
        runId,
        type,
        type + " message",
        accountId,
        symbol,
        positionId,
        executionId,
        orderId,
        clOrdId,
        journalEntryId,
        ledgerEntryId
    );
    ReflectionTestUtils.setField(anomaly, "id", id);
    return anomaly;
  }

  private LedgerReconciliationCase repairPendingCase(LedgerIntegrityAnomalyRecord anomaly, Instant asOf) {
    LedgerReconciliationCase reconciliationCase = LedgerReconciliationCase.openFromAnomaly(anomaly, asOf.minusSeconds(60));
    reconciliationCase.transitionTo(LedgerReconciliationCaseStatus.REPAIR_PENDING, asOf);
    return reconciliationCase;
  }

  private LedgerReconciliationCase acknowledgedCase(LedgerIntegrityAnomalyRecord anomaly, Instant asOf) {
    LedgerReconciliationCase reconciliationCase = LedgerReconciliationCase.openFromAnomaly(anomaly, asOf.minusSeconds(60));
    reconciliationCase.transitionTo(LedgerReconciliationCaseStatus.ACKNOWLEDGED, asOf);
    return reconciliationCase;
  }

  private LedgerReconciliationCase waivedCase(LedgerIntegrityAnomalyRecord anomaly, Instant asOf) {
    LedgerReconciliationCase reconciliationCase = LedgerReconciliationCase.openFromAnomaly(anomaly, asOf.minusSeconds(60));
    reconciliationCase.transitionTo(LedgerReconciliationCaseStatus.WAIVED, asOf);
    return reconciliationCase;
  }
}
