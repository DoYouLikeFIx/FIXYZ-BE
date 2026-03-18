package com.fix.corebank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepOutboundOrderPayload;
import com.fix.corebank.entity.LedgerIntegrityAnomalyRecord;
import com.fix.corebank.entity.LedgerReconciliationCase;
import com.fix.corebank.entity.LedgerReconciliationRepair;
import com.fix.corebank.repository.LedgerIntegrityAnomalyRecordRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseRepository;
import com.fix.corebank.repository.LedgerReconciliationRepairRepository;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.service.LedgerIntegrityService;
import com.fix.corebank.service.LedgerReconciliationService;
import com.fix.corebank.service.LedgerRepairService;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.LedgerReconciliationCaseCreateCommand;
import com.fix.corebank.vo.LedgerReconciliationCaseResult;
import com.fix.corebank.vo.LedgerReconciliationRepairCommand;
import com.fix.corebank.vo.LedgerReconciliationRepairResult;
import com.fix.corebank.vo.LedgerReconciliationRerunCommand;
import com.fix.corebank.vo.LedgerReconciliationRerunResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret",
    "corebank.ledger-integrity.enabled=false"
})
class LedgerRepairIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final long ACCOUNT_ID = 1L;
  private static final String SELL_SYMBOL = "005930";
  private static final String BUY_SYMBOL = "000660";
  private static final Instant EXECUTED_AT = Instant.parse("2026-03-01T10:05:30Z");

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private LedgerIntegrityService ledgerIntegrityService;

  @Autowired
  private LedgerReconciliationService ledgerReconciliationService;

  @Autowired
  private LedgerRepairService ledgerRepairService;

  @Autowired
  private LedgerIntegrityAnomalyRecordRepository anomalyRecordRepository;

  @Autowired
  private LedgerReconciliationCaseRepository caseRepository;

  @Autowired
  private LedgerReconciliationRepairRepository repairRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockBean
  private FepClient fepClient;

  @BeforeEach
  void setUp() {
    reset(fepClient);
    jdbcTemplate.update("DELETE FROM ledger_reconciliation_repairs");
    jdbcTemplate.update("DELETE FROM ledger_reconciliation_case_events");
    jdbcTemplate.update("DELETE FROM ledger_reconciliation_cases");
    jdbcTemplate.update("DELETE FROM ledger_integrity_anomalies");
    jdbcTemplate.update("DELETE FROM ledger_integrity_runs");
    jdbcTemplate.update("DELETE FROM ledger_entry_refs");
    jdbcTemplate.update("DELETE FROM ledger_entries");
    jdbcTemplate.update("DELETE FROM journal_entries");
    jdbcTemplate.update("DELETE FROM executions");
    jdbcTemplate.update("DELETE FROM orders");
    jdbcTemplate.update("DELETE FROM positions");
    jdbcTemplate.update(
        "UPDATE accounts SET status = 'ACTIVE', cash_balance = 100000000.0000, daily_sell_limit = 500.0000 WHERE id = 1"
    );
    jdbcTemplate.update(
        """
            INSERT INTO positions (account_id, symbol, qty, avg_price, created_at, updated_at, version)
            VALUES (1, '005930', 120.0000, 70000.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """
    );

    when(fepClient.submitOrder(any(FepOutboundOrderPayload.class), anyString()))
        .thenAnswer(invocation -> toFilledResult(invocation.getArgument(0)));
  }

  @Test
  void shouldApplyAttachLedgerReferenceRepairIdempotentlyAndResolveOnRerun() {
    LedgerReconciliationCaseResult reconciliationCase = createCaseFromMissingReferenceAnomaly();
    int refsBeforeRepair = count("ledger_entry_refs");

    LedgerReconciliationRepairResult first = ledgerRepairService.applyRepair(
        LedgerReconciliationRepairCommand.of(
            reconciliationCase.getCaseId(),
            "repair-key-attach-1",
            "ATTACH_LEDGER_CL_ORD_REF",
            "attach missing ref",
            "ops-repair",
            "repair-ctx",
            "corr-repair-1"
        )
    );
    LedgerReconciliationRepairResult replay = ledgerRepairService.applyRepair(
        LedgerReconciliationRepairCommand.of(
            reconciliationCase.getCaseId(),
            "repair-key-attach-1",
            "ATTACH_LEDGER_CL_ORD_REF",
            "attach missing ref",
            "ops-repair",
            "repair-ctx",
            "corr-repair-1"
        )
    );

    assertThat(first.isIdempotent()).isFalse();
    assertThat(first.isMutated()).isTrue();
    assertThat(first.getCaseStatus()).isEqualTo("REPAIR_PENDING");
    assertThat(count("ledger_entry_refs")).isEqualTo(refsBeforeRepair + 1);
    assertThat(repairRepository.count()).isEqualTo(1);
    assertThat(replay.isIdempotent()).isTrue();
    assertThat(replay.getRepairId()).isEqualTo(first.getRepairId());
    assertThat(count("ledger_entry_refs")).isEqualTo(refsBeforeRepair + 1);

    LedgerReconciliationRerunResult rerun = ledgerRepairService.rerunCase(
        LedgerReconciliationRerunCommand.of(
            reconciliationCase.getCaseId(),
            "verify attach repair",
            "ops-rerun",
            "rerun-ctx",
            "corr-rerun-1"
        )
    );

    assertThat(rerun.isAnomalyStillPresent()).isFalse();
    assertThat(rerun.getCurrentStatus()).isEqualTo("RESOLVED");
    assertThat(rerun.getRerunRunId()).isNotNull();

    LedgerReconciliationRepair savedRepair = repairRepository.findByCaseIdAndRepairKey(
        reconciliationCase.getCaseId(),
        "repair-key-attach-1"
    ).orElseThrow();
    assertThat(savedRepair.getRerunRunId()).isEqualTo(rerun.getRerunRunId());
    assertThat(savedRepair.getRerunCaseStatus()).isEqualTo("RESOLVED");
  }

  @Test
  void shouldReopenCaseWhenSameAnomalyStillExistsAfterRepairRerun() {
    LedgerReconciliationCaseResult reconciliationCase = createCaseFromMissingReferenceAnomaly();

    LedgerReconciliationRepairResult repair = ledgerRepairService.applyRepair(
        LedgerReconciliationRepairCommand.of(
            reconciliationCase.getCaseId(),
            "repair-key-attach-2",
            "ATTACH_LEDGER_CL_ORD_REF",
            "attach ref before rerun",
            "ops-repair",
            "repair-ctx",
            "corr-repair-2"
        )
    );
    assertThat(repair.getCaseStatus()).isEqualTo("REPAIR_PENDING");

    LedgerReconciliationCase savedCase = caseRepository.findById(reconciliationCase.getCaseId()).orElseThrow();
    jdbcTemplate.update(
        "DELETE FROM ledger_entry_refs WHERE ledger_entry_id = ? AND ref_type = ? AND ref_value = ?",
        savedCase.getLedgerEntryId(),
        "CL_ORD_ID",
        savedCase.getClOrdId()
    );

    LedgerReconciliationRerunResult rerun = ledgerRepairService.rerunCase(
        LedgerReconciliationRerunCommand.of(
            reconciliationCase.getCaseId(),
            "verify unresolved anomaly",
            "ops-rerun",
            "rerun-ctx",
            "corr-rerun-2"
        )
    );

    assertThat(rerun.isAnomalyStillPresent()).isTrue();
    assertThat(rerun.getCurrentStatus()).isEqualTo("REOPENED");
    assertThat(caseRepository.findById(reconciliationCase.getCaseId()).orElseThrow().getStatus().name())
        .isEqualTo("REOPENED");
  }

  @Test
  void shouldRebuildPositionFromExecutionsWithoutMutatingHistoricalRows() {
    createFilledOrder(BUY_SYMBOL, "BUY", "5.0000", "120000.0000");
    jdbcTemplate.update(
        "UPDATE positions SET qty = -1.0000 WHERE account_id = ? AND symbol = ?",
        ACCOUNT_ID,
        BUY_SYMBOL
    );
    LedgerReconciliationCaseResult reconciliationCase = createCaseFromLatestAnomaly();
    int executionsBefore = count("executions");
    int journalsBefore = count("journal_entries");

    LedgerReconciliationRepairResult repair = ledgerRepairService.applyRepair(
        LedgerReconciliationRepairCommand.of(
            reconciliationCase.getCaseId(),
            "repair-key-rebuild-1",
            "REBUILD_POSITION_FROM_EXECUTIONS",
            "rebuild corrupted position",
            "ops-repair",
            "repair-ctx",
            "corr-rebuild-1"
        )
    );

    assertThat(repair.isMutated()).isTrue();
    assertThat(repair.getCaseStatus()).isEqualTo("REPAIR_PENDING");
    assertThat(count("executions")).isEqualTo(executionsBefore);
    assertThat(count("journal_entries")).isEqualTo(journalsBefore);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
        BigDecimal.class,
        ACCOUNT_ID,
        BUY_SYMBOL
    )).isEqualByComparingTo("5.0000");

    LedgerReconciliationRerunResult rerun = ledgerRepairService.rerunCase(
        LedgerReconciliationRerunCommand.of(
            reconciliationCase.getCaseId(),
            "verify rebuilt position",
            "ops-rerun",
            "rerun-ctx",
            "corr-rebuild-rerun"
        )
    );

    assertThat(rerun.isAnomalyStillPresent()).isFalse();
    assertThat(rerun.getCurrentStatus()).isEqualTo("RESOLVED");
  }

  @Test
  void shouldAllowMarkFalsePositiveWithoutDataMutation() {
    LedgerReconciliationCaseResult reconciliationCase = createCaseFromMissingReferenceAnomaly();
    int refsBefore = count("ledger_entry_refs");

    LedgerReconciliationRepairResult repair = ledgerRepairService.applyRepair(
        LedgerReconciliationRepairCommand.of(
            reconciliationCase.getCaseId(),
            "repair-key-fp-1",
            "MARK_FALSE_POSITIVE",
            "known false positive",
            "ops-reviewer",
            "fp-ctx",
            "corr-fp-1"
        )
    );

    assertThat(repair.isMutated()).isFalse();
    assertThat(repair.getOutcome()).isEqualTo("NO_OP");
    assertThat(repair.getCaseStatus()).isEqualTo("WAIVED");
    assertThat(count("ledger_entry_refs")).isEqualTo(refsBefore);
  }

  @Test
  void shouldRejectUnsupportedRepairType() {
    LedgerReconciliationCaseResult reconciliationCase = createCaseFromMissingReferenceAnomaly();

    assertThatThrownBy(() -> ledgerRepairService.applyRepair(
        LedgerReconciliationRepairCommand.of(
            reconciliationCase.getCaseId(),
            "repair-key-invalid-1",
            "UNSUPPORTED",
            "bad type",
            "ops-reviewer",
            "invalid-ctx",
            "corr-invalid-1"
        )
    )).isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);
  }

  private LedgerReconciliationCaseResult createCaseFromMissingReferenceAnomaly() {
    createFilledOrder(SELL_SYMBOL, "SELL", "10.0000", "72000.0000");
    Long ledgerEntryId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM ledger_entries", Long.class);
    jdbcTemplate.update("DELETE FROM ledger_entry_refs WHERE ledger_entry_id = ?", ledgerEntryId);
    return createCaseFromLatestAnomaly();
  }

  private LedgerReconciliationCaseResult createCaseFromLatestAnomaly() {
    ledgerIntegrityService.runCheckAndStore();
    LedgerIntegrityAnomalyRecord anomaly = anomalyRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))
        .stream()
        .findFirst()
        .orElseThrow();
    return ledgerReconciliationService.createCase(
        LedgerReconciliationCaseCreateCommand.of(
            anomaly.getId(),
            "open repair case",
            "ops-open",
            "case-open-ctx",
            "corr-case-open"
        )
    );
  }

  private String createFilledOrder(String symbol, String side, String qty, String price) {
    String clOrdId = UUID.randomUUID().toString();
    corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        clOrdId,
        symbol,
        side,
        new BigDecimal(qty),
        new BigDecimal(price)
    ));
    return clOrdId;
  }

  private FepOrderResult toFilledResult(FepOutboundOrderPayload payload) {
    return new FepOrderResult(
        payload.clOrdId(),
        "FEP-KRX-" + payload.clOrdId(),
        FepExecType.FILL,
        FepOrdStatus.FILLED,
        payload.qty(),
        payload.price(),
        0L,
        EXECUTED_AT,
        EXECUTED_AT,
        null,
        null,
        null,
        null
    );
  }

  private int count(String tableName) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    return count == null ? 0 : count;
  }
}
