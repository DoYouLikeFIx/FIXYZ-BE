package com.fix.corebank.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_migration;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class CoreFlywayMigrationTest {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("V3 migration should create owner key + default status + numeric account constraints")
  void shouldCreateMemberTableAndSeedData() {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member", Integer.class);
    assertThat(count).isNotNull();
    assertThat(count).isGreaterThanOrEqualTo(1);
  }

  @Test
  void shouldEnforceMemberOwnerUniquenessAndStatusDefault() {
    jdbcTemplate.update("INSERT INTO member (id, member_no, email) VALUES (?, ?, ?)", 2L, "M-2002", "seed2@fix.local");

    jdbcTemplate.update(
        "INSERT INTO accounts (id, account_no, member_id, currency, cash_balance, daily_sell_limit, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
        2L, "220123456789", 2L, "KRW", 1000000.0000, 500.0000, 0L
    );

    String status = jdbcTemplate.queryForObject("SELECT status FROM accounts WHERE id = 2", String.class);
    assertThat(status).isEqualTo("ACTIVE");

    assertThatThrownBy(() -> jdbcTemplate.update(
        "INSERT INTO accounts (id, account_no, member_id, status, currency, cash_balance, daily_sell_limit, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
        3L, "220123456790", 2L, "ACTIVE", "KRW", 1000000.0000, 500.0000, 0L
    )).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void shouldRejectNonNumericAccountNumber() {
    jdbcTemplate.update("INSERT INTO member (id, member_no, email) VALUES (?, ?, ?)", 3L, "M-3003", "seed3@fix.local");

    assertThatThrownBy(() -> jdbcTemplate.update(
        "INSERT INTO accounts (id, account_no, member_id, status, currency, cash_balance, daily_sell_limit, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
        4L, "ACC-3003", 3L, "ACTIVE", "KRW", 1000000.0000, 500.0000, 0L
    )).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void shouldCreateAccountStatusEventsTableAndIndexes() {
    Integer tableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'ACCOUNT_STATUS_EVENTS'",
        Integer.class
    );
    Integer previousStatusLength = jdbcTemplate.queryForObject(
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ACCOUNT_STATUS_EVENTS' AND COLUMN_NAME = 'PREVIOUS_STATUS'",
        Integer.class
    );
    Integer accountIndexCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'ACCOUNT_STATUS_EVENTS' "
            + "AND INDEX_NAME = 'IDX_ACCOUNT_STATUS_EVENTS_ACCOUNT_ID_CREATED_AT'",
        Integer.class
    );
    Integer memberIndexCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'ACCOUNT_STATUS_EVENTS' "
            + "AND INDEX_NAME = 'IDX_ACCOUNT_STATUS_EVENTS_MEMBER_ID_CREATED_AT'",
        Integer.class
    );

    assertThat(tableCount).isNotNull();
    assertThat(tableCount).isEqualTo(1);
    assertThat(previousStatusLength).isEqualTo(16);
    assertThat(accountIndexCount).isEqualTo(1);
    assertThat(memberIndexCount).isEqualTo(1);
  }

  @Test
  void shouldFixMoneyScaleToDecimal194() {
    Integer cashBalanceScale = jdbcTemplate.queryForObject(
        "SELECT NUMERIC_SCALE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'ACCOUNTS' AND COLUMN_NAME = 'CASH_BALANCE'",
        Integer.class
    );
    Integer dailySellLimitScale = jdbcTemplate.queryForObject(
        "SELECT NUMERIC_SCALE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'ACCOUNTS' AND COLUMN_NAME = 'DAILY_SELL_LIMIT'",
        Integer.class
    );

    assertThat(cashBalanceScale).isEqualTo(4);
    assertThat(dailySellLimitScale).isEqualTo(4);
  }

  @Test
  void shouldAddExecutionSummaryColumnsToOrders() {
    Integer executionResultLength = jdbcTemplate.queryForObject(
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDERS' AND COLUMN_NAME = 'EXECUTION_RESULT'",
        Integer.class
    );
    Integer executedQtyScale = jdbcTemplate.queryForObject(
        "SELECT NUMERIC_SCALE FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDERS' AND COLUMN_NAME = 'EXECUTED_QTY'",
        Integer.class
    );
    Integer leavesQtyScale = jdbcTemplate.queryForObject(
        "SELECT NUMERIC_SCALE FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDERS' AND COLUMN_NAME = 'LEAVES_QTY'",
        Integer.class
    );
    Integer executedPriceScale = jdbcTemplate.queryForObject(
        "SELECT NUMERIC_SCALE FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDERS' AND COLUMN_NAME = 'EXECUTED_PRICE'",
        Integer.class
    );
    Integer executedAtExists = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'ORDERS' AND COLUMN_NAME = 'EXECUTED_AT'",
        Integer.class
    );

    assertThat(executionResultLength).isEqualTo(32);
    assertThat(executedQtyScale).isEqualTo(4);
    assertThat(leavesQtyScale).isEqualTo(4);
    assertThat(executedPriceScale).isEqualTo(4);
    assertThat(executedAtExists).isEqualTo(1);
  }

  @Test
  void shouldCreateLedgerIntegrityTrackingTables() {
    Integer runTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'LEDGER_INTEGRITY_RUNS'",
        Integer.class
    );
    Integer anomalyTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'LEDGER_INTEGRITY_ANOMALIES'",
        Integer.class
    );
    Integer summaryMessageLength = jdbcTemplate.queryForObject(
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'LEDGER_INTEGRITY_RUNS' AND COLUMN_NAME = 'SUMMARY_MESSAGE'",
        Integer.class
    );
    Integer checkedAtExists = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'LEDGER_INTEGRITY_RUNS' AND COLUMN_NAME = 'CHECKED_AT'",
        Integer.class
    );
    Integer runIndexCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'LEDGER_INTEGRITY_RUNS' "
            + "AND INDEX_NAME = 'IDX_LEDGER_INTEGRITY_RUNS_CHECKED_AT'",
        Integer.class
    );
    Integer anomalyRunIndexCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'LEDGER_INTEGRITY_ANOMALIES' "
            + "AND INDEX_NAME = 'IDX_LEDGER_INTEGRITY_ANOMALIES_RUN_ID'",
        Integer.class
    );
    Integer anomalyTypeIndexCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'LEDGER_INTEGRITY_ANOMALIES' "
            + "AND INDEX_NAME = 'IDX_LEDGER_INTEGRITY_ANOMALIES_TYPE'",
        Integer.class
    );

    assertThat(runTableCount).isEqualTo(1);
    assertThat(anomalyTableCount).isEqualTo(1);
    assertThat(summaryMessageLength).isEqualTo(500);
    assertThat(checkedAtExists).isEqualTo(1);
    assertThat(runIndexCount).isEqualTo(1);
    assertThat(anomalyRunIndexCount).isEqualTo(1);
    assertThat(anomalyTypeIndexCount).isEqualTo(1);
  }

  @Test
  void shouldCreateLedgerReconciliationTables() {
    Integer caseTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'LEDGER_RECONCILIATION_CASES'",
        Integer.class
    );
    Integer eventTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'LEDGER_RECONCILIATION_CASE_EVENTS'",
        Integer.class
    );
    Integer statusLength = jdbcTemplate.queryForObject(
        "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'LEDGER_RECONCILIATION_CASES' AND COLUMN_NAME = 'STATUS'",
        Integer.class
    );
    Integer lastTransitionExists = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE TABLE_NAME = 'LEDGER_RECONCILIATION_CASES' AND COLUMN_NAME = 'LAST_TRANSITION_AT'",
        Integer.class
    );
    Integer anomalyIndexCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'LEDGER_RECONCILIATION_CASES' "
            + "AND INDEX_NAME = 'IDX_LEDGER_RECONCILIATION_CASES_ANOMALY_ID'",
        Integer.class
    );
    Integer eventCaseIndexCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
            + "WHERE TABLE_NAME = 'LEDGER_RECONCILIATION_CASE_EVENTS' "
            + "AND INDEX_NAME = 'IDX_LEDGER_RECONCILIATION_CASE_EVENTS_CASE_ID'",
        Integer.class
    );

    assertThat(caseTableCount).isEqualTo(1);
    assertThat(eventTableCount).isEqualTo(1);
    assertThat(statusLength).isEqualTo(32);
    assertThat(lastTransitionExists).isEqualTo(1);
    assertThat(anomalyIndexCount).isEqualTo(1);
    assertThat(eventCaseIndexCount).isEqualTo(1);
  }
}
