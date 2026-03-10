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
}
