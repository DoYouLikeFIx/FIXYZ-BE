package com.fix.corebank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.corebank.client.FepQuoteSnapshotClient;
import com.fix.corebank.client.FepQuoteSnapshotResult;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.fix.corebank.vo.AccountPositionQueryCommand;
import com.fix.corebank.vo.AccountPositionResult;
import com.fix.corebank.vo.AccountPositionsQueryCommand;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "corebank.market-data.max-quote-age-ms=5000",
    "internal.secret=test-secret"
})
@Import(CorebankAccountPositionRepeatableReadIntegrationTest.FixedClockConfig.class)
class CorebankAccountPositionRepeatableReadIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final Instant FIXED_NOW = Instant.parse("2026-03-20T00:00:06Z");

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @MockBean
  private FepQuoteSnapshotClient fepQuoteSnapshotClient;

  private CountDownLatch quoteLookupStarted;
  private CountDownLatch allowQuoteLookup;

  @BeforeEach
  void setUp() throws InterruptedException {
    ReflectionTestUtils.setField(corebankOrderService, "limitWindowClock", Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    jdbcTemplate.update("DELETE FROM executions");
    jdbcTemplate.update("DELETE FROM positions");
    jdbcTemplate.update(
        "UPDATE accounts SET member_id = 1, status = 'ACTIVE', cash_balance = 100000000.0000, daily_sell_limit = 500.0000, updated_at = ? WHERE id = 1",
        Timestamp.from(Instant.parse("2026-03-20T00:00:00Z"))
    );
    jdbcTemplate.update(
        """
        INSERT INTO positions (account_id, symbol, qty, avg_price, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        1L,
        "005930",
        new BigDecimal("120.0000"),
        new BigDecimal("70000.0000"),
        Timestamp.from(Instant.parse("2026-03-20T00:00:00Z")),
        Timestamp.from(Instant.parse("2026-03-20T00:00:00Z")),
        0
    );
    jdbcTemplate.update(
        """
        INSERT INTO executions (
          order_id, account_id, cl_ord_id, symbol, side, exec_qty, exec_price, executed_at, created_at, updated_at, version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        0L,
        1L,
        "RR-BUY-001",
        "005930",
        "BUY",
        new BigDecimal("130.0000"),
        new BigDecimal("70000.0000"),
        Timestamp.from(Instant.parse("2026-03-19T23:50:00Z")),
        Timestamp.from(Instant.parse("2026-03-19T23:50:00Z")),
        Timestamp.from(Instant.parse("2026-03-19T23:50:00Z")),
        0
    );
    jdbcTemplate.update(
        """
        INSERT INTO executions (
          order_id, account_id, cl_ord_id, symbol, side, exec_qty, exec_price, executed_at, created_at, updated_at, version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        0L,
        1L,
        "RR-SELL-001",
        "005930",
        "SELL",
        new BigDecimal("10.0000"),
        new BigDecimal("70500.0000"),
        Timestamp.from(Instant.parse("2026-03-20T00:00:01Z")),
        Timestamp.from(Instant.parse("2026-03-20T00:00:01Z")),
        Timestamp.from(Instant.parse("2026-03-20T00:00:01Z")),
        0
    );

    quoteLookupStarted = new CountDownLatch(1);
    allowQuoteLookup = new CountDownLatch(1);

    reset(fepQuoteSnapshotClient);
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshot(eq("005930"), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenAnswer(invocation -> {
          quoteLookupStarted.countDown();
          assertThat(allowQuoteLookup.await(5, TimeUnit.SECONDS)).isTrue();
          return new FepQuoteSnapshotResult(
              "qsnap-005930-live-rr",
              "005930",
              FepQuoteSourceMode.LIVE,
              FIXED_NOW.minusSeconds(1),
              72000L,
              72100L,
              72050L,
              42L,
              false
          );
        });
    when(fepQuoteSnapshotClient.queryLatestQuoteSnapshots(eq(List.of("005930")), eq(FepQuoteSourceMode.LIVE), anyString()))
        .thenAnswer(invocation -> {
          quoteLookupStarted.countDown();
          assertThat(allowQuoteLookup.await(5, TimeUnit.SECONDS)).isTrue();
          return Map.of(
              "005930",
              new FepQuoteSnapshotResult(
                  "qsnap-005930-live-rr",
                  "005930",
                  FepQuoteSourceMode.LIVE,
                  FIXED_NOW.minusSeconds(1),
                  72000L,
                  72100L,
                  72050L,
                  42L,
                  false
              )
          );
        });
  }

  @Test
  void shouldReturnOneCoherentSnapshotAcrossPositionBalanceAndPnlDuringConcurrentMutation() throws Exception {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    try {
      Future<AccountPositionResult> future = executorService.submit(() -> repeatableReadTemplate().execute(status ->
          corebankOrderService.getAccountPosition(AccountPositionQueryCommand.of(1L, 1L, "005930"))
      ));

      assertThat(quoteLookupStarted.await(5, TimeUnit.SECONDS)).isTrue();

      requiresNewTemplate().execute(status -> {
        Instant mutatedAt = Instant.parse("2026-03-20T00:00:08Z");
        jdbcTemplate.update(
            "UPDATE accounts SET cash_balance = ?, updated_at = ? WHERE id = 1",
            new BigDecimal("90000000.0000"),
            Timestamp.from(mutatedAt)
        );
        jdbcTemplate.update(
            "UPDATE positions SET qty = ?, avg_price = ?, updated_at = ? WHERE account_id = 1 AND symbol = '005930'",
            new BigDecimal("80.0000"),
            new BigDecimal("68000.0000"),
            Timestamp.from(mutatedAt)
        );
        jdbcTemplate.update(
            """
            INSERT INTO executions (
              order_id, account_id, cl_ord_id, symbol, side, exec_qty, exec_price, executed_at, created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            0L,
            1L,
            "RR-SELL-002",
            "005930",
            "SELL",
            new BigDecimal("40.0000"),
            new BigDecimal("71000.0000"),
            Timestamp.from(Instant.parse("2026-03-20T00:00:02Z")),
            Timestamp.from(mutatedAt),
            Timestamp.from(mutatedAt),
            0
        );
        return null;
      });

      allowQuoteLookup.countDown();

      AccountPositionResult result = future.get(10, TimeUnit.SECONDS);

      assertThat(result.getQuantity()).isEqualByComparingTo("120.0000");
      assertThat(result.getBalance()).isEqualByComparingTo("100000000.0000");
      assertThat(result.getAvgPrice()).isEqualByComparingTo("70000.0000");
      assertThat(result.getMarketPrice()).isEqualByComparingTo("72050.0000");
      assertThat(result.getUnrealizedPnl()).isEqualByComparingTo("246000.0000");
      assertThat(result.getRealizedPnlDaily()).isEqualByComparingTo("5000.0000");
      assertThat(result.getQuoteSnapshotId()).isEqualTo("qsnap-005930-live-rr");

      assertThat(jdbcTemplate.queryForObject(
          "SELECT cash_balance FROM accounts WHERE id = 1",
          BigDecimal.class
      )).isEqualByComparingTo("90000000.0000");
      assertThat(jdbcTemplate.queryForObject(
          "SELECT qty FROM positions WHERE account_id = 1 AND symbol = '005930'",
          BigDecimal.class
      )).isEqualByComparingTo("80.0000");
    } finally {
      allowQuoteLookup.countDown();
      executorService.shutdownNow();
    }
  }

  @Test
  void shouldReturnOneCoherentSnapshotAcrossPositionListDuringConcurrentMutation() throws Exception {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    try {
      Future<List<AccountPositionResult>> future = executorService.submit(() -> repeatableReadTemplate().execute(status ->
          corebankOrderService.getAccountPositions(AccountPositionsQueryCommand.of(1L, 1L))
      ));

      assertThat(quoteLookupStarted.await(5, TimeUnit.SECONDS)).isTrue();

      requiresNewTemplate().execute(status -> {
        Instant mutatedAt = Instant.parse("2026-03-20T00:00:08Z");
        jdbcTemplate.update(
            "UPDATE accounts SET cash_balance = ?, updated_at = ? WHERE id = 1",
            new BigDecimal("90000000.0000"),
            Timestamp.from(mutatedAt)
        );
        jdbcTemplate.update(
            "UPDATE positions SET qty = ?, avg_price = ?, updated_at = ? WHERE account_id = 1 AND symbol = '005930'",
            new BigDecimal("80.0000"),
            new BigDecimal("68000.0000"),
            Timestamp.from(mutatedAt)
        );
        jdbcTemplate.update(
            """
            INSERT INTO executions (
              order_id, account_id, cl_ord_id, symbol, side, exec_qty, exec_price, executed_at, created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            0L,
            1L,
            "RR-SELL-003",
            "005930",
            "SELL",
            new BigDecimal("40.0000"),
            new BigDecimal("71000.0000"),
            Timestamp.from(Instant.parse("2026-03-20T00:00:02Z")),
            Timestamp.from(mutatedAt),
            Timestamp.from(mutatedAt),
            0
        );
        return null;
      });

      allowQuoteLookup.countDown();

      List<AccountPositionResult> result = future.get(10, TimeUnit.SECONDS);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getQuantity()).isEqualByComparingTo("120.0000");
      assertThat(result.get(0).getBalance()).isEqualByComparingTo("100000000.0000");
      assertThat(result.get(0).getAvgPrice()).isEqualByComparingTo("70000.0000");
      assertThat(result.get(0).getMarketPrice()).isEqualByComparingTo("72050.0000");
      assertThat(result.get(0).getUnrealizedPnl()).isEqualByComparingTo("246000.0000");
      assertThat(result.get(0).getRealizedPnlDaily()).isEqualByComparingTo("5000.0000");
      assertThat(result.get(0).getQuoteSnapshotId()).isEqualTo("qsnap-005930-live-rr");
    } finally {
      allowQuoteLookup.countDown();
      executorService.shutdownNow();
    }
  }

  private TransactionTemplate repeatableReadTemplate() {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return transactionTemplate;
  }

  private TransactionTemplate requiresNewTemplate() {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return transactionTemplate;
  }

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    Clock quoteFreshnessClock() {
      return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }
  }
}
