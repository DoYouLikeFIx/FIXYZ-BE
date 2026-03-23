package com.fix.corebank.integration;

import static com.fix.corebank.support.CorebankLiquidityFixtures.seedRestingBuyLiquidity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepOutboundOrderPayload;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.service.CorebankOrderPersistenceService;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.service.OrderPreparationLockHook;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret",
    "corebank.order.position-lock-timeout-millis=-1"
})
class PositionConcurrencyIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final long ACCOUNT_ID = 1L;
  private static final String SYMBOL = "005930";
  private static final int THREAD_COUNT = 10;
  private static final BigDecimal ORDER_QTY = new BigDecimal("100.0000");
  private static final BigDecimal ORDER_PRICE = new BigDecimal("72000.0000");
  private static final Instant EXECUTED_AT = Instant.parse("2026-03-01T10:05:30Z");

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private CorebankOrderPersistenceService orderPersistenceService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private OrderRepository orderRepository;

  @MockBean
  private FepClient fepClient;

  @MockBean
  private OrderPreparationLockHook orderPreparationLockHook;

  @BeforeEach
  void setUp() {
    reset(fepClient);
    reset(orderPreparationLockHook);
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
            VALUES (1, '005930', 500.0000, 70000.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """
    );

    when(fepClient.submitOrder(any(FepOutboundOrderPayload.class), anyString()))
        .thenAnswer(invocation -> toFilledResult(invocation.getArgument(0)));
  }

  @Test
  @Timeout(20)
  void shouldAllowExactlyFiveFilledSellOrdersWithoutOversellUnderTenThreadLoad() throws Exception {
    seedRestingBuyLiquidity(
        jdbcTemplate,
        orderRepository,
        2L,
        2L,
        "200000000002",
        SYMBOL,
        "maker-" + UUID.randomUUID(),
        new BigDecimal("500.0000"),
        ORDER_PRICE
    );
    CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);

    try {
      List<Future<AttemptOutcome>> futures = new ArrayList<>();
      for (int i = 0; i < THREAD_COUNT; i++) {
        String clOrdId = UUID.randomUUID().toString();
        futures.add(executorService.submit(taskFor(clOrdId, SYMBOL, ready, start)));
      }

      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

      long startedAt = System.nanoTime();
      start.countDown();

      List<AttemptOutcome> outcomes = new ArrayList<>();
      for (Future<AttemptOutcome> future : futures) {
        outcomes.add(future.get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS));
      }
      Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

      List<AttemptOutcome> successes = outcomes.stream()
          .filter(AttemptOutcome::success)
          .toList();
      List<AttemptOutcome> failures = outcomes.stream()
          .filter(outcome -> !outcome.success())
          .toList();

      assertThat(elapsed).isLessThanOrEqualTo(Duration.ofSeconds(5));
      assertThat(successes).hasSize(5);
      assertThat(successes)
          .allSatisfy(outcome -> {
            assertThat(outcome.status()).isEqualTo("FILLED");
            assertThat(outcome.executionResult()).isEqualTo("FILLED");
          });
      assertThat(failures).hasSize(5);
      assertThat(failures)
          .allSatisfy(outcome -> assertThat(outcome.errorCode()).isEqualTo(ErrorCode.ORD_INSUFFICIENT_POSITION));

      assertThat(accountCashBalance()).isEqualByComparingTo("136000000.0000");
      assertThat(positionQuantity(SYMBOL)).isEqualByComparingTo("0.0000");
      assertThat(positionQuantity(SYMBOL).signum()).isNotNegative();
      assertThat(count("orders")).isEqualTo(6);
      assertThat(count("executions")).isEqualTo(10);
      assertThat(count("journal_entries")).isEqualTo(10);
      assertThat(count("ledger_entries")).isEqualTo(20);
      assertThat(count("ledger_entry_refs")).isEqualTo(20);

      verify(fepClient, times(5)).submitOrder(any(FepOutboundOrderPayload.class), anyString());
    } finally {
      executorService.shutdownNow();
      executorService.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  @Timeout(20)
  void shouldAllowDifferentSymbolsToProceedWhileAnotherSymbolPositionLockIsHeld() throws Exception {
    String firstSymbol = SYMBOL;
    String secondSymbol = "000660";
    insertPosition(secondSymbol, "100.0000", "120000.0000");

    CountDownLatch firstPositionLocked = new CountDownLatch(1);
    CountDownLatch secondPositionLocked = new CountDownLatch(1);
    CountDownLatch releaseFirstOrder = new CountDownLatch(1);
    CountDownLatch releaseSecondOrder = new CountDownLatch(1);
    AtomicBoolean shouldBlockFirstOrder = new AtomicBoolean(true);
    AtomicBoolean shouldBlockSecondOrder = new AtomicBoolean(true);

    doAnswer(invocation -> {
      Long accountId = invocation.getArgument(0);
      String symbol = invocation.getArgument(1);
      if (ACCOUNT_ID == accountId && firstSymbol.equals(symbol) && shouldBlockFirstOrder.compareAndSet(true, false)) {
        firstPositionLocked.countDown();
        releaseFirstOrder.await(15, TimeUnit.SECONDS);
      } else if (ACCOUNT_ID == accountId && secondSymbol.equals(symbol) && shouldBlockSecondOrder.compareAndSet(true, false)) {
        secondPositionLocked.countDown();
        releaseSecondOrder.await(15, TimeUnit.SECONDS);
      }
      return null;
    }).when(orderPreparationLockHook).afterPositionLock(anyLong(), anyString());

    ExecutorService executorService = Executors.newFixedThreadPool(2);
    Future<CorebankOrderPersistenceService.PendingOrderSubmission> firstFuture = null;
    Future<CorebankOrderPersistenceService.PendingOrderSubmission> secondFuture = null;

    try {
      firstFuture = executorService.submit(() -> prepareSellSubmission(UUID.randomUUID().toString(), firstSymbol));
      assertThat(firstPositionLocked.await(5, TimeUnit.SECONDS)).isTrue();

      secondFuture = executorService.submit(() -> prepareSellSubmission(UUID.randomUUID().toString(), secondSymbol));
      assertThat(secondPositionLocked.await(5, TimeUnit.SECONDS)).isTrue();

      releaseSecondOrder.countDown();
      CorebankOrderPersistenceService.PendingOrderSubmission secondSubmission = secondFuture.get(10, TimeUnit.SECONDS);
      releaseFirstOrder.countDown();
      CorebankOrderPersistenceService.PendingOrderSubmission firstSubmission = firstFuture.get(10, TimeUnit.SECONDS);

      assertThat(secondSubmission.symbol()).isEqualTo(secondSymbol);
      assertThat(secondSubmission.status()).isEqualTo("NEW");
      assertThat(firstSubmission.symbol()).isEqualTo(firstSymbol);
      assertThat(firstSubmission.status()).isEqualTo("NEW");

      assertThat(accountCashBalance()).isEqualByComparingTo("100000000.0000");
      assertThat(positionQuantity(firstSymbol)).isEqualByComparingTo("500.0000");
      assertThat(positionQuantity(secondSymbol)).isEqualByComparingTo("100.0000");
      assertThat(count("orders")).isEqualTo(2);
      assertThat(count("executions")).isEqualTo(0);
      assertThat(count("journal_entries")).isEqualTo(0);
      assertThat(count("ledger_entries")).isEqualTo(0);
      assertThat(count("ledger_entry_refs")).isEqualTo(0);

      verify(fepClient, times(0)).submitOrder(any(FepOutboundOrderPayload.class), anyString());
    } finally {
      releaseSecondOrder.countDown();
      releaseFirstOrder.countDown();
      executorService.shutdownNow();
      executorService.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private Callable<AttemptOutcome> taskFor(
      String clOrdId,
      String symbol,
      CountDownLatch ready,
      CountDownLatch start
  ) {
    return () -> {
      ready.countDown();
      assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
      return attemptSell(clOrdId, symbol);
    };
  }

  private AttemptOutcome attemptSell(String clOrdId, String symbol) {
    try {
      InternalOrderResult result = corebankOrderService.createOrder(InternalOrderCreateCommand.of(
          ACCOUNT_ID,
          clOrdId,
          symbol,
          "SELL",
          ORDER_QTY,
          ORDER_PRICE
      ));
      return AttemptOutcome.success(clOrdId, result.getStatus(), result.getExecutionResult());
    } catch (BusinessException ex) {
      return AttemptOutcome.failure(clOrdId, ex.getErrorCode());
    }
  }

  private CorebankOrderPersistenceService.PendingOrderSubmission prepareSellSubmission(String clOrdId, String symbol) {
    return orderPersistenceService.prepareOrderSubmission(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        clOrdId,
        symbol,
        "SELL",
        ORDER_QTY,
        ORDER_PRICE
    ));
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

  private BigDecimal positionQuantity(String symbol) {
    return jdbcTemplate.queryForObject(
        "SELECT qty FROM positions WHERE account_id = 1 AND symbol = ?",
        BigDecimal.class,
        symbol
    );
  }

  private void insertPosition(String symbol, String qty, String avgPrice) {
    jdbcTemplate.update(
        """
            INSERT INTO positions (account_id, symbol, qty, avg_price, created_at, updated_at, version)
            VALUES (1, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """,
        symbol,
        new BigDecimal(qty),
        new BigDecimal(avgPrice)
    );
  }

  private int count(String tableName) {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    return count == null ? 0 : count;
  }

  private record AttemptOutcome(
      String clOrdId,
      boolean success,
      String status,
      String executionResult,
      ErrorCode errorCode
  ) {

    private static AttemptOutcome success(String clOrdId, String status, String executionResult) {
      return new AttemptOutcome(clOrdId, true, status, executionResult, null);
    }

    private static AttemptOutcome failure(String clOrdId, ErrorCode errorCode) {
      return new AttemptOutcome(clOrdId, false, null, null, errorCode);
    }
  }
}
