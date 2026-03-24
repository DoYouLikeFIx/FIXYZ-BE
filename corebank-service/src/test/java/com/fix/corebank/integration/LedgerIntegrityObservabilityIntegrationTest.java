package com.fix.corebank.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import com.fix.corebank.entity.LedgerIntegrityRun;
import com.fix.corebank.entity.LedgerReconciliationCase;
import com.fix.corebank.entity.LedgerReconciliationCaseStatus;
import com.fix.corebank.repository.LedgerIntegrityAnomalyRecordRepository;
import com.fix.corebank.repository.LedgerIntegrityRunRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseRepository;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret",
    "corebank.ledger-integrity.enabled=false",
    "corebank.ledger-integrity.observability.stale-after=PT30M",
    "corebank.ledger-integrity.observability.alert.unresolved-backlog-threshold=1000",
    "corebank.ledger-integrity.observability.alert.repair-pending-threshold=1000",
    "corebank.ledger-integrity.observability.alert.critical-anomaly-threshold=1000"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LedgerIntegrityObservabilityIntegrationTest extends CorebankContainersIntegrationTestBase {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private LedgerIntegrityRunRepository runRepository;

  @Autowired
  private LedgerIntegrityAnomalyRecordRepository anomalyRepository;

  @Autowired
  private LedgerReconciliationCaseRepository caseRepository;

  @MockBean
  private FepClient fepClient;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM ledger_reconciliation_repairs");
    jdbcTemplate.update("DELETE FROM ledger_reconciliation_case_events");
    jdbcTemplate.update("DELETE FROM ledger_reconciliation_cases");
    jdbcTemplate.update("DELETE FROM ledger_integrity_anomalies");
    jdbcTemplate.update("DELETE FROM ledger_integrity_runs");
  }

  @Test
  void shouldReturnSummaryWithLatestRunBacklogAndFailedIdentifiers() throws Exception {
    LedgerIntegrityRun failedRun = runRepository.saveAndFlush(LedgerIntegrityRun.of(
        Instant.parse("2026-03-01T09:00:00Z"),
        false,
        2,
        "NEGATIVE_POSITION: negative position (+1 more)"
    ));
    LedgerIntegrityAnomalyRecord critical = anomalyRepository.saveAndFlush(LedgerIntegrityAnomalyRecord.of(
        failedRun.getId(),
        "NEGATIVE_POSITION",
        "negative position",
        1L,
        "005930",
        11L,
        null,
        101L,
        "123e4567-e89b-42d3-a456-426614174301",
        201L,
        301L
    ));
    LedgerIntegrityAnomalyRecord missingRef = anomalyRepository.saveAndFlush(LedgerIntegrityAnomalyRecord.of(
        failedRun.getId(),
        "MISSING_LEDGER_CL_ORD_REF",
        "missing CL_ORD_ID ledger reference",
        1L,
        "005930",
        11L,
        null,
        102L,
        "123e4567-e89b-42d3-a456-426614174302",
        202L,
        302L
    ));
    LedgerReconciliationCase repairPending = LedgerReconciliationCase.openFromAnomaly(
        critical,
        Instant.parse("2026-03-01T09:01:00Z")
    );
    repairPending.transitionTo(
        LedgerReconciliationCaseStatus.REPAIR_PENDING,
        Instant.parse("2026-03-01T09:02:00Z")
    );
    caseRepository.saveAndFlush(repairPending);

    LedgerIntegrityRun latestPassedRun = runRepository.saveAndFlush(LedgerIntegrityRun.of(
        Instant.parse("2026-03-01T09:05:00Z"),
        true,
        0,
        "Ledger integrity check passed"
    ));

    mockMvc.perform(get("/internal/v1/ledger-integrity/summary")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-ledger-summary"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-ledger-summary"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.latestRunId").value(latestPassedRun.getId()))
        .andExpect(jsonPath("$.data.latestRunPassed").value(true))
        .andExpect(jsonPath("$.data.latestRunAnomalyCount").value(0))
        .andExpect(jsonPath("$.data.latestRunSummaryMessage").value("Ledger integrity check passed"))
        .andExpect(jsonPath("$.data.unresolvedAnomalyCount").value(2))
        .andExpect(jsonPath("$.data.repairPendingCount").value(1))
        .andExpect(jsonPath("$.data.criticalAnomalyCount").value(1))
        .andExpect(jsonPath("$.data.staleLastRun").value(true))
        .andExpect(jsonPath("$.data.latestFailedRunId").value(failedRun.getId()))
        .andExpect(jsonPath("$.data.latestFailedIdentifiers[0].anomalyId").value(critical.getId()))
        .andExpect(jsonPath("$.data.latestFailedIdentifiers[0].anomalyType").value("NEGATIVE_POSITION"))
        .andExpect(jsonPath("$.data.latestFailedIdentifiers[0].clOrdId").value("123e4567-e89b-42d3-a456-426614174301"))
        .andExpect(jsonPath("$.data.latestFailedIdentifiers[1].anomalyId").value(missingRef.getId()))
        .andExpect(jsonPath("$.data.latestFailedIdentifiers[1].anomalyType").value("MISSING_LEDGER_CL_ORD_REF"))
        .andExpect(jsonPath("$.data.latestFailedIdentifiers[1].clOrdId").value("123e4567-e89b-42d3-a456-426614174302"));
  }

  @Test
  void shouldRejectLedgerIntegritySummaryWithoutInternalSecret() throws Exception {
    mockMvc.perform(get("/internal/v1/ledger-integrity/summary")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-ledger-summary-unauthorized"))
        .andExpect(status().isForbidden())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-ledger-summary-unauthorized"))
        .andExpect(jsonPath("$.code").value("AUTH-003"))
        .andExpect(jsonPath("$.message").value("Missing or invalid X-Internal-Secret"))
        .andExpect(jsonPath("$.path").value("/internal/v1/ledger-integrity/summary"));
  }

  @Test
  void shouldReturnEmptySummaryWhenNoIntegrityRunsExist() throws Exception {
    mockMvc.perform(get("/internal/v1/ledger-integrity/summary")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "corr-ledger-summary-empty"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "corr-ledger-summary-empty"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.latestRunId").doesNotExist())
        .andExpect(jsonPath("$.data.latestRunCheckedAt").doesNotExist())
        .andExpect(jsonPath("$.data.latestRunPassed").doesNotExist())
        .andExpect(jsonPath("$.data.latestRunAnomalyCount").doesNotExist())
        .andExpect(jsonPath("$.data.latestRunSummaryMessage").doesNotExist())
        .andExpect(jsonPath("$.data.unresolvedAnomalyCount").value(0))
        .andExpect(jsonPath("$.data.repairPendingCount").value(0))
        .andExpect(jsonPath("$.data.criticalAnomalyCount").value(0))
        .andExpect(jsonPath("$.data.staleLastRun").value(true))
        .andExpect(jsonPath("$.data.latestFailedRunId").doesNotExist())
        .andExpect(jsonPath("$.data.latestFailedIdentifiers").isEmpty());
  }
}
