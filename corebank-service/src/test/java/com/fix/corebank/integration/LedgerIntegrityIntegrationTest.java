package com.fix.corebank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepOutboundOrderPayload;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.service.LedgerIntegrityService;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.LedgerIntegrityAnomaly;
import com.fix.corebank.vo.LedgerIntegrityCheckResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret"
})
class LedgerIntegrityIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final long ACCOUNT_ID = 1L;
  private static final String SELL_SYMBOL = "005930";
  private static final String BUY_SYMBOL = "000660";
  private static final Instant EXECUTED_AT = Instant.parse("2026-03-01T10:05:30Z");

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private LedgerIntegrityService ledgerIntegrityService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockBean
  private FepClient fepClient;

  @BeforeEach
  void setUp() {
    reset(fepClient);
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
  void shouldPassWhenCompletedOrdersHaveBalancedLedgerEvidence() {
    createFilledOrder(SELL_SYMBOL, "SELL", "10.0000", "72000.0000");
    createFilledOrder(BUY_SYMBOL, "BUY", "5.0000", "120000.0000");

    LedgerIntegrityCheckResult result = ledgerIntegrityService.runCheck();

    assertThat(result.isPassed()).isTrue();
    assertThat(result.getAnomalyCount()).isZero();
    assertThat(result.getAnomalies()).isEmpty();
    assertThat(result.getCheckedAt()).isNotNull();
  }

  @Test
  void shouldReportNegativePositionWithTraceableIdentifiers() {
    jdbcTemplate.update(
        "UPDATE positions SET qty = -1.0000 WHERE account_id = ? AND symbol = ?",
        ACCOUNT_ID,
        SELL_SYMBOL
    );

    LedgerIntegrityCheckResult result = ledgerIntegrityService.runCheck();

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getAnomalies()).singleElement().satisfies(anomaly -> {
      assertThat(anomaly.getType()).isEqualTo("NEGATIVE_POSITION");
      assertThat(anomaly.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(anomaly.getSymbol()).isEqualTo(SELL_SYMBOL);
      assertThat(anomaly.getPositionId()).isNotNull();
      assertThat(anomaly.getMessage()).contains("negative");
    });
  }

  @Test
  void shouldReportOrphanExecutionWithOrderIdentifiers() {
    String clOrdId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        """
            INSERT INTO executions (
              order_id, account_id, cl_ord_id, symbol, side, exec_qty, exec_price, executed_at, created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """,
        999999L,
        ACCOUNT_ID,
        clOrdId,
        SELL_SYMBOL,
        "SELL",
        new BigDecimal("5.0000"),
        new BigDecimal("72000.0000"),
        EXECUTED_AT
    );

    LedgerIntegrityCheckResult result = ledgerIntegrityService.runCheck();

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getAnomalies()).singleElement().satisfies(anomaly -> {
      assertThat(anomaly.getType()).isEqualTo("ORPHAN_EXECUTION");
      assertThat(anomaly.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(anomaly.getSymbol()).isEqualTo(SELL_SYMBOL);
      assertThat(anomaly.getExecutionId()).isNotNull();
      assertThat(anomaly.getOrderId()).isEqualTo(999999L);
      assertThat(anomaly.getClOrdId()).isEqualTo(clOrdId);
    });
  }

  @Test
  void shouldReportJournalLedgerCountMismatch() {
    String clOrdId = createFilledOrder(SELL_SYMBOL, "SELL", "10.0000", "72000.0000");
    Long journalEntryId = jdbcTemplate.queryForObject("SELECT id FROM journal_entries", Long.class);
    Long extraLedgerEntryId = insertZeroAmountLedgerEntry(journalEntryId, ACCOUNT_ID, clOrdId);
    insertLedgerReference(extraLedgerEntryId, clOrdId);

    LedgerIntegrityCheckResult result = ledgerIntegrityService.runCheck();

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getAnomalies())
        .anySatisfy(anomaly -> {
          assertThat(anomaly.getType()).isEqualTo("JOURNAL_LEDGER_COUNT_MISMATCH");
          assertThat(anomaly.getJournalEntryId()).isEqualTo(journalEntryId);
          assertThat(anomaly.getOrderId()).isNotNull();
          assertThat(anomaly.getClOrdId()).isEqualTo(clOrdId);
        });
  }

  @Test
  void shouldReportJournalLedgerBalanceMismatch() {
    createFilledOrder(SELL_SYMBOL, "SELL", "10.0000", "72000.0000");
    Long firstLedgerEntryId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM ledger_entries", Long.class);
    jdbcTemplate.update(
        "UPDATE ledger_entries SET amount = amount + 1.0000 WHERE id = ?",
        firstLedgerEntryId
    );

    LedgerIntegrityCheckResult result = ledgerIntegrityService.runCheck();

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getAnomalies())
        .anySatisfy(anomaly -> {
          assertThat(anomaly.getType()).isEqualTo("JOURNAL_LEDGER_BALANCE_MISMATCH");
          assertThat(anomaly.getJournalEntryId()).isNotNull();
          assertThat(anomaly.getMessage()).contains("debit=").contains("credit=");
        });
  }

  @Test
  void shouldReportMissingLedgerReferenceWithTraceableIdentifiers() {
    String clOrdId = createFilledOrder(SELL_SYMBOL, "SELL", "10.0000", "72000.0000");
    Long ledgerEntryId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM ledger_entries", Long.class);
    jdbcTemplate.update("DELETE FROM ledger_entry_refs WHERE ledger_entry_id = ?", ledgerEntryId);

    LedgerIntegrityCheckResult result = ledgerIntegrityService.runCheck();

    assertThat(result.isPassed()).isFalse();
    assertThat(result.getAnomalies())
        .anySatisfy(anomaly -> {
          assertThat(anomaly.getType()).isEqualTo("MISSING_LEDGER_CL_ORD_REF");
          assertThat(anomaly.getAccountId()).isEqualTo(ACCOUNT_ID);
          assertThat(anomaly.getClOrdId()).isEqualTo(clOrdId);
          assertThat(anomaly.getJournalEntryId()).isNotNull();
          assertThat(anomaly.getLedgerEntryId()).isEqualTo(ledgerEntryId);
        });
  }

  @Test
  void shouldPersistSuccessfulIntegrityRunSummary() {
    createFilledOrder(SELL_SYMBOL, "SELL", "10.0000", "72000.0000");

    LedgerIntegrityCheckResult result = ledgerIntegrityService.runCheckAndStore();

    assertThat(result.isPassed()).isTrue();
    assertThat(count("ledger_integrity_runs")).isEqualTo(1);
    assertThat(count("ledger_integrity_anomalies")).isEqualTo(0);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT passed FROM ledger_integrity_runs",
        Boolean.class
    )).isTrue();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT anomaly_count FROM ledger_integrity_runs",
        Integer.class
    )).isEqualTo(0);
  }

  @Test
  void shouldPersistAnomalyEvidenceForFailedIntegrityRun() {
    jdbcTemplate.update(
        "UPDATE positions SET qty = -1.0000 WHERE account_id = ? AND symbol = ?",
        ACCOUNT_ID,
        SELL_SYMBOL
    );

    LedgerIntegrityCheckResult result = ledgerIntegrityService.runCheckAndStore();

    assertThat(result.isPassed()).isFalse();
    assertThat(count("ledger_integrity_runs")).isEqualTo(1);
    assertThat(count("ledger_integrity_anomalies")).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT type FROM ledger_integrity_anomalies",
        String.class
    )).isEqualTo("NEGATIVE_POSITION");
    assertThat(jdbcTemplate.queryForObject(
        "SELECT account_id FROM ledger_integrity_anomalies",
        Long.class
    )).isEqualTo(ACCOUNT_ID);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT symbol FROM ledger_integrity_anomalies",
        String.class
    )).isEqualTo(SELL_SYMBOL);
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

  private Long insertZeroAmountLedgerEntry(Long journalEntryId, Long accountId, String clOrdId) {
    jdbcTemplate.update(
        """
            INSERT INTO ledger_entries (
              journal_entry_id, account_id, ledger_type, direction, amount, created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """,
        journalEntryId,
        accountId,
        "POSITION",
        "DR",
        BigDecimal.ZERO.setScale(4)
    );
    return jdbcTemplate.queryForObject(
        "SELECT MAX(id) FROM ledger_entries WHERE journal_entry_id = ?",
        Long.class,
        journalEntryId
    );
  }

  private void insertLedgerReference(Long ledgerEntryId, String clOrdId) {
    jdbcTemplate.update(
        """
            INSERT INTO ledger_entry_refs (
              ledger_entry_id, ref_type, ref_value, created_at, updated_at, version
            ) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """,
        ledgerEntryId,
        "CL_ORD_ID",
        clOrdId
    );
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
