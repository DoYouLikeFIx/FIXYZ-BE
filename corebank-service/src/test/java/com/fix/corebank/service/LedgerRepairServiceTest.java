package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import com.fix.corebank.entity.LedgerReconciliationCase;
import com.fix.corebank.entity.LedgerReconciliationCaseEvent;
import com.fix.corebank.entity.LedgerReconciliationCaseStatus;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.LedgerEntryRefRepository;
import com.fix.corebank.repository.LedgerIntegrityAnomalyRecordRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseEventRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseRepository;
import com.fix.corebank.repository.LedgerReconciliationRepairRepository;
import com.fix.corebank.repository.PositionRepository;
import com.fix.corebank.vo.LedgerIntegrityCheckResult;
import com.fix.corebank.vo.LedgerReconciliationRerunCommand;
import com.fix.corebank.vo.LedgerReconciliationRerunResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerRepairServiceTest {

  @Mock
  private LedgerReconciliationCaseRepository caseRepository;

  @Mock
  private LedgerReconciliationCaseEventRepository eventRepository;

  @Mock
  private LedgerReconciliationRepairRepository repairRepository;

  @Mock
  private LedgerEntryRefRepository ledgerEntryRefRepository;

  @Mock
  private PositionRepository positionRepository;

  @Mock
  private ExecutionRepository executionRepository;

  @Mock
  private LedgerIntegrityService ledgerIntegrityService;

  @Mock
  private LedgerIntegrityAnomalyRecordRepository anomalyRecordRepository;

  @Mock
  private LedgerIntegrityObservabilityService ledgerIntegrityObservabilityService;

  private LedgerRepairService service;

  @BeforeEach
  void setUp() {
    service = new LedgerRepairService(
        caseRepository,
        eventRepository,
        repairRepository,
        ledgerEntryRefRepository,
        positionRepository,
        executionRepository,
        ledgerIntegrityService,
        anomalyRecordRepository,
        ledgerIntegrityObservabilityService
    );
  }

  @Test
  void shouldRefreshObservabilityOnlyOnceAfterRerunCaseTransitionsStatus() {
    LedgerIntegrityAnomalyRecord anomaly = LedgerIntegrityAnomalyRecord.of(
        71L,
        "NEGATIVE_POSITION",
        "negative position",
        1L,
        "005930",
        11L,
        null,
        101L,
        "123e4567-e89b-42d3-a456-426614174231",
        201L,
        301L
    );
    ReflectionTestUtils.setField(anomaly, "id", 801L);

    LedgerReconciliationCase reconciliationCase = LedgerReconciliationCase.openFromAnomaly(
        anomaly,
        Instant.parse("2026-03-01T09:00:00Z")
    );
    reconciliationCase.transitionTo(
        LedgerReconciliationCaseStatus.REPAIR_PENDING,
        Instant.parse("2026-03-01T09:01:00Z")
    );
    ReflectionTestUtils.setField(reconciliationCase, "id", 901L);

    LedgerIntegrityCheckResult rerunCheck = LedgerIntegrityCheckResult.of(
        Instant.parse("2026-03-01T09:02:00Z"),
        List.of()
    ).withRunId(1001L);
    LedgerReconciliationCaseEvent rerunEvent = LedgerReconciliationCaseEvent.statusChanged(
        901L,
        LedgerReconciliationCaseStatus.REPAIR_PENDING,
        LedgerReconciliationCaseStatus.RESOLVED,
        "rerun resolved",
        "ops-reviewer",
        "ctx-rerun",
        "corr-rerun"
    );
    ReflectionTestUtils.setField(rerunEvent, "id", 1101L);
    ReflectionTestUtils.setField(rerunEvent, "createdAt", Instant.parse("2026-03-01T09:02:01Z"));

    when(caseRepository.findById(901L)).thenReturn(Optional.of(reconciliationCase));
    when(ledgerIntegrityService.runCheckAndStore(false)).thenReturn(rerunCheck);
    when(anomalyRecordRepository.findAllByRunId(1001L)).thenReturn(List.of());
    when(caseRepository.saveAndFlush(any(LedgerReconciliationCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(eventRepository.saveAndFlush(any(LedgerReconciliationCaseEvent.class))).thenReturn(rerunEvent);
    when(repairRepository.findFirstByCaseIdOrderByIdDesc(901L)).thenReturn(Optional.empty());

    LedgerReconciliationRerunResult result = service.rerunCase(
        LedgerReconciliationRerunCommand.of(
            901L,
            "rerun resolved",
            "ops-reviewer",
            "ctx-rerun",
            "corr-rerun"
        )
    );

    assertThat(result.getCurrentStatus()).isEqualTo("RESOLVED");
    assertThat(result.getRerunRunId()).isEqualTo(1001L);
    verify(ledgerIntegrityService, times(1)).runCheckAndStore(false);
    verify(ledgerIntegrityService, never()).runCheckAndStore();
    verify(ledgerIntegrityObservabilityService, times(1)).refreshMetricsAndEvaluateAlertsAfterCommit();
  }
}
