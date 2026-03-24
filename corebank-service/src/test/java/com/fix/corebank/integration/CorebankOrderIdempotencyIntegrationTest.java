package com.fix.corebank.integration;

import static com.fix.corebank.support.CorebankLiquidityFixtures.seedRestingSellLiquidity;
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
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "corebank.order.position-lock-timeout-millis=-1"
})
class CorebankOrderIdempotencyIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final long ACCOUNT_ID = 1L;
  private static final String SYMBOL = "005930";
  private static final BigDecimal ORDER_QTY = new BigDecimal("10.0000");
  private static final BigDecimal ORDER_PRICE = new BigDecimal("72000.0000");
  private static final Instant EXECUTED_AT = Instant.parse("2026-03-01T10:05:30Z");

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private OrderRepository orderRepository;

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
  @Timeout(20)
  void e10_004ShouldCommitOnlyOnePostingPathForConcurrentDuplicateOrderRequests() throws Exception {
    String clOrdId = UUID.randomUUID().toString();
    seedRestingSellLiquidity(
        jdbcTemplate,
        orderRepository,
        2L,
        2L,
        "200000000002",
        SYMBOL,
        "maker-" + clOrdId,
        ORDER_QTY,
        ORDER_PRICE
    );
    InternalOrderCreateCommand command = InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        clOrdId,
        SYMBOL,
        "BUY",
        ORDER_QTY,
        ORDER_PRICE
    );
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executorService = Executors.newFixedThreadPool(2);

    Callable<InternalOrderResult> task = () -> {
      ready.countDown();
      start.await(3, TimeUnit.SECONDS);
      return corebankOrderService.createOrder(command);
    };

    try {
      Future<InternalOrderResult> firstFuture = executorService.submit(task);
      Future<InternalOrderResult> secondFuture = executorService.submit(task);

      assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      InternalOrderResult first = firstFuture.get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
      InternalOrderResult second = secondFuture.get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);

      assertThat(first.getOrderId()).isEqualTo(second.getOrderId());
      assertThat(List.of(first.isIdempotent(), second.isIdempotent())).containsExactlyInAnyOrder(false, true);
      assertThat(count("orders")).isEqualTo(2);
      assertThat(count("executions")).isEqualTo(2);
      assertThat(count("journal_entries")).isEqualTo(2);
      assertThat(count("ledger_entries")).isEqualTo(4);
      assertThat(count("ledger_entry_refs")).isEqualTo(4);
      assertThat(accountCashBalance()).isEqualByComparingTo("99280000.0000");
      assertThat(positionQuantity()).isEqualByComparingTo("130.0000");
    } finally {
      executorService.shutdownNow();
      executorService.awaitTermination(5, TimeUnit.SECONDS);
    }
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
        null,
        null,
        null,
        null,
        null
    );
  }

  private BigDecimal accountCashBalance() {
    return jdbcTemplate.queryForObject(
        "SELECT cash_balance FROM accounts WHERE id = 1",
        BigDecimal.class
    );
  }

  private BigDecimal positionQuantity() {
    return jdbcTemplate.queryForObject(
        "SELECT qty FROM positions WHERE account_id = 1 AND symbol = '005930'",
        BigDecimal.class
    );
  }

  private int count(String tableName) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    return count == null ? 0 : count;
  }
}
