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
import com.fix.corebank.entity.LedgerReconciliationCaseEvent;
import com.fix.corebank.repository.LedgerIntegrityAnomalyRecordRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseEventRepository;
import com.fix.corebank.repository.LedgerReconciliationCaseRepository;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.service.LedgerIntegrityService;
import com.fix.corebank.service.LedgerReconciliationService;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.LedgerReconciliationCaseCreateCommand;
import com.fix.corebank.vo.LedgerReconciliationCaseResult;
import com.fix.corebank.vo.LedgerReconciliationCaseTransitionCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
class LedgerReconciliationIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final long ACCOUNT_ID = 1L;
  private static final String SELL_SYMBOL = "005930";
  private static final Instant EXECUTED_AT = Instant.parse("2026-03-01T10:05:30Z");

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private LedgerIntegrityService ledgerIntegrityService;

  @Autowired
  private LedgerReconciliationService ledgerReconciliationService;

  @Autowired
  private LedgerIntegrityAnomalyRecordRepository anomalyRecordRepository;

  @Autowired
  private LedgerReconciliationCaseRepository caseRepository;

  @Autowired
  private LedgerReconciliationCaseEventRepository eventRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockBean
  private FepClient fepClient;

  @BeforeEach
  void setUp() {
    reset(fepClient);
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
  void shouldCreateReconciliationCaseFromStoredAnomalyWithTraceableSnapshot() {
    LedgerIntegrityAnomalyRecord anomaly = createStoredMissingReferenceAnomaly();

    LedgerReconciliationCaseResult result = ledgerReconciliationService.createCase(
        LedgerReconciliationCaseCreateCommand.of(
            anomaly.getId(),
            "open reconciliation case",
            "ops-user",
            "story-5.8",
            "corr-case-create"
        )
    );

    assertThat(result.isCreated()).isTrue();
    assertThat(result.isChanged()).isTrue();
    assertThat(result.getCurrentStatus()).isEqualTo("NEW");
    assertThat(result.getEventId()).isNotNull();
    assertThat(caseRepository.count()).isEqualTo(1);
    assertThat(eventRepository.count()).isEqualTo(1);

    LedgerReconciliationCase savedCase = caseRepository.findById(result.getCaseId()).orElseThrow();
    assertThat(savedCase.getAnomalyId()).isEqualTo(anomaly.getId());
    assertThat(savedCase.getRunId()).isEqualTo(anomaly.getRunId());
    assertThat(savedCase.getAnomalyType()).isEqualTo(anomaly.getType());
    assertThat(savedCase.getSummaryMessage()).isEqualTo(anomaly.getMessage());
    assertThat(savedCase.getAccountId()).isEqualTo(anomaly.getAccountId());
    assertThat(savedCase.getSymbol()).isEqualTo(anomaly.getSymbol());
    assertThat(savedCase.getOrderId()).isEqualTo(anomaly.getOrderId());
    assertThat(savedCase.getClOrdId()).isEqualTo(anomaly.getClOrdId());
    assertThat(savedCase.getJournalEntryId()).isEqualTo(anomaly.getJournalEntryId());
    assertThat(savedCase.getLedgerEntryId()).isEqualTo(anomaly.getLedgerEntryId());
    assertThat(savedCase.getLastTransitionAt()).isNotNull();

    List<LedgerReconciliationCaseEvent> events = eventRepository.findByCaseIdOrderByIdAsc(savedCase.getId());
    assertThat(events).singleElement().satisfies(event -> {
      assertThat(event.getEventType().name()).isEqualTo("CREATED");
      assertThat(event.getPreviousStatus()).isNull();
      assertThat(event.getNewStatus().name()).isEqualTo("NEW");
      assertThat(event.getReason()).isEqualTo("open reconciliation case");
      assertThat(event.getActor()).isEqualTo("ops-user");
      assertThat(event.getContext()).isEqualTo("story-5.8");
      assertThat(event.getCorrelationId()).isEqualTo("corr-case-create");
      assertThat(event.getCreatedAt()).isNotNull();
    });
  }

  @Test
  void shouldReturnExistingUnresolvedCaseForDuplicateCreateRequest() {
    LedgerIntegrityAnomalyRecord anomaly = createStoredNegativePositionAnomaly();

    LedgerReconciliationCaseResult first = ledgerReconciliationService.createCase(
        LedgerReconciliationCaseCreateCommand.of(anomaly.getId(), "first open", "ops-a", "ctx-a", "corr-a")
    );
    LedgerReconciliationCaseResult second = ledgerReconciliationService.createCase(
        LedgerReconciliationCaseCreateCommand.of(anomaly.getId(), "duplicate open", "ops-b", "ctx-b", "corr-b")
    );

    assertThat(first.isCreated()).isTrue();
    assertThat(second.isCreated()).isFalse();
    assertThat(second.isChanged()).isFalse();
    assertThat(second.getCaseId()).isEqualTo(first.getCaseId());
    assertThat(caseRepository.count()).isEqualTo(1);
    assertThat(eventRepository.count()).isEqualTo(1);
  }

  @Test
  void shouldTransitionCaseToAcknowledgedAndRecordAuditEvent() {
    LedgerReconciliationCaseResult created = ledgerReconciliationService.createCase(
        LedgerReconciliationCaseCreateCommand.of(
            createStoredNegativePositionAnomaly().getId(),
            "open",
            "ops-a",
            "ctx-open",
            "corr-open"
        )
    );

    LedgerReconciliationCaseResult transitioned = ledgerReconciliationService.transitionCase(
        LedgerReconciliationCaseTransitionCommand.of(
            created.getCaseId(),
            "ACKNOWLEDGED",
            "triaged by operator",
            "ops-reviewer",
            "story-5.8-ack",
            "corr-ack"
        )
    );

    assertThat(transitioned.isChanged()).isTrue();
    assertThat(transitioned.isCreated()).isFalse();
    assertThat(transitioned.getPreviousStatus()).isEqualTo("NEW");
    assertThat(transitioned.getCurrentStatus()).isEqualTo("ACKNOWLEDGED");

    List<LedgerReconciliationCaseEvent> events = eventRepository.findByCaseIdOrderByIdAsc(created.getCaseId());
    assertThat(events).hasSize(2);
    LedgerReconciliationCaseEvent transitionEvent = events.get(1);
    assertThat(transitionEvent.getEventType().name()).isEqualTo("STATUS_CHANGED");
    assertThat(transitionEvent.getPreviousStatus().name()).isEqualTo("NEW");
    assertThat(transitionEvent.getNewStatus().name()).isEqualTo("ACKNOWLEDGED");
    assertThat(transitionEvent.getReason()).isEqualTo("triaged by operator");
    assertThat(transitionEvent.getActor()).isEqualTo("ops-reviewer");
    assertThat(transitionEvent.getContext()).isEqualTo("story-5.8-ack");
    assertThat(transitionEvent.getCorrelationId()).isEqualTo("corr-ack");
  }

  @Test
  void shouldAllowDirectWaiveAndRepairPendingTransitionsFromNew() {
    LedgerReconciliationCaseResult waivedCase = ledgerReconciliationService.createCase(
        LedgerReconciliationCaseCreateCommand.of(
            createStoredNegativePositionAnomaly().getId(),
            "open waive candidate",
            "ops-a",
            "ctx-open-waive",
            "corr-open-waive"
        )
    );

    LedgerReconciliationCaseResult waived = ledgerReconciliationService.transitionCase(
        LedgerReconciliationCaseTransitionCommand.of(
            waivedCase.getCaseId(),
            "WAIVED",
            "false positive reviewed",
            "ops-waive",
            "ctx-waive",
            "corr-waive"
        )
    );
    assertThat(waived.getCurrentStatus()).isEqualTo("WAIVED");

    jdbcTemplate.update("DELETE FROM ledger_reconciliation_case_events");
    jdbcTemplate.update("DELETE FROM ledger_reconciliation_cases");
    jdbcTemplate.update("DELETE FROM ledger_integrity_anomalies");
    jdbcTemplate.update("DELETE FROM ledger_integrity_runs");
    LedgerIntegrityAnomalyRecord secondAnomaly = createStoredNegativePositionAnomaly();
    LedgerReconciliationCaseResult repairPendingCase = ledgerReconciliationService.createCase(
        LedgerReconciliationCaseCreateCommand.of(
            secondAnomaly.getId(),
            "open repair candidate",
            "ops-b",
            "ctx-open-repair",
            "corr-open-repair"
        )
    );

    LedgerReconciliationCaseResult repairPending = ledgerReconciliationService.transitionCase(
        LedgerReconciliationCaseTransitionCommand.of(
            repairPendingCase.getCaseId(),
            "REPAIR_PENDING",
            "needs guarded repair",
            "ops-repair",
            "ctx-repair",
            "corr-repair"
        )
    );

    assertThat(repairPending.getCurrentStatus()).isEqualTo("REPAIR_PENDING");
    assertThat(eventRepository.findByCaseIdOrderByIdAsc(repairPendingCase.getCaseId())).hasSize(2);
  }

  @Test
  void shouldRejectInvalidTransitionFromTerminalCase() {
    LedgerReconciliationCaseResult created = ledgerReconciliationService.createCase(
        LedgerReconciliationCaseCreateCommand.of(
            createStoredNegativePositionAnomaly().getId(),
            "open terminal candidate",
            "ops-a",
            "ctx-open-terminal",
            "corr-open-terminal"
        )
    );
    ledgerReconciliationService.transitionCase(
        LedgerReconciliationCaseTransitionCommand.of(
            created.getCaseId(),
            "WAIVED",
            "waived after review",
            "ops-waive",
            "ctx-waive-terminal",
            "corr-waive-terminal"
        )
    );

    assertThatThrownBy(() -> ledgerReconciliationService.transitionCase(
        LedgerReconciliationCaseTransitionCommand.of(
            created.getCaseId(),
            "REPAIR_PENDING",
            "should be rejected",
            "ops-invalid",
            "ctx-invalid",
            "corr-invalid"
        )
    )).isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);

    assertThat(eventRepository.findByCaseIdOrderByIdAsc(created.getCaseId())).hasSize(2);
  }

  private LedgerIntegrityAnomalyRecord createStoredNegativePositionAnomaly() {
    jdbcTemplate.update(
        "UPDATE positions SET qty = -1.0000 WHERE account_id = ? AND symbol = ?",
        ACCOUNT_ID,
        SELL_SYMBOL
    );
    ledgerIntegrityService.runCheckAndStore();
    return latestAnomaly();
  }

  private LedgerIntegrityAnomalyRecord createStoredMissingReferenceAnomaly() {
    createFilledOrder(SELL_SYMBOL, "SELL", "10.0000", "72000.0000");
    Long ledgerEntryId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM ledger_entries", Long.class);
    jdbcTemplate.update("DELETE FROM ledger_entry_refs WHERE ledger_entry_id = ?", ledgerEntryId);
    ledgerIntegrityService.runCheckAndStore();
    return latestAnomaly();
  }

  private LedgerIntegrityAnomalyRecord latestAnomaly() {
    return anomalyRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))
        .stream()
        .findFirst()
        .orElseThrow();
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
}
