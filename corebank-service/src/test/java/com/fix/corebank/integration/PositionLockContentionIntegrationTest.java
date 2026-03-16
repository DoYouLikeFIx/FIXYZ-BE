package com.fix.corebank.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepOutboundOrderPayload;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.service.OrderPreparationLockHook;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret",
    "corebank.order.position-lock-timeout-millis=0"
})
@AutoConfigureMockMvc
class PositionLockContentionIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final long ACCOUNT_ID = 1L;
  private static final String SYMBOL = "005930";
  private static final BigDecimal ORDER_QTY = new BigDecimal("100.0000");
  private static final BigDecimal ORDER_PRICE = new BigDecimal("72000.0000");
  private static final Instant EXECUTED_AT = Instant.parse("2026-03-01T10:05:30Z");

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private MockMvc mockMvc;

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
  void shouldReturnCore003WhenSameSymbolPositionLockContentionExceedsTimeout() throws Exception {
    String firstClOrdId = UUID.randomUUID().toString();
    String secondClOrdId = UUID.randomUUID().toString();
    CountDownLatch firstPositionLocked = new CountDownLatch(1);
    CountDownLatch releaseFirstOrder = new CountDownLatch(1);
    AtomicBoolean shouldBlockFirstOrder = new AtomicBoolean(true);

    doAnswer(invocation -> {
      Long accountId = invocation.getArgument(0);
      String symbol = invocation.getArgument(1);
      if (ACCOUNT_ID == accountId && SYMBOL.equals(symbol) && shouldBlockFirstOrder.compareAndSet(true, false)) {
        firstPositionLocked.countDown();
        assertThat(releaseFirstOrder.await(5, TimeUnit.SECONDS)).isTrue();
      }
      return null;
    }).when(orderPreparationLockHook).afterPositionLock(anyLong(), anyString());

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    try {
      Future<?> firstOrder = executorService.submit(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
          ACCOUNT_ID,
          firstClOrdId,
          SYMBOL,
          "SELL",
          ORDER_QTY,
          ORDER_PRICE
      )));

      assertThat(firstPositionLocked.await(5, TimeUnit.SECONDS)).isTrue();

      mockMvc.perform(post("/internal/v1/orders")
              .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
              .header(CommonHeaders.X_CORRELATION_ID, "trace-core-position-lock")
              .param("accountId", String.valueOf(ACCOUNT_ID))
              .param("clOrdId", secondClOrdId)
              .param("symbol", SYMBOL)
              .param("side", "SELL")
              .param("quantity", ORDER_QTY.toPlainString())
              .param("price", ORDER_PRICE.toPlainString()))
          .andExpect(status().isConflict())
          .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-position-lock"))
          .andExpect(jsonPath("$.code").value("CORE-003"))
          .andExpect(jsonPath("$.message").value("Concurrent modification conflict"))
          .andExpect(jsonPath("$.userMessageKey").value("error.core.concurrency_conflict"))
          .andExpect(jsonPath("$.operatorCode").value("CONCURRENCY_FAILURE"))
          .andExpect(jsonPath("$.details.failureReason").value("POSITION_LOCK"))
          .andExpect(jsonPath("$.details.symbol").value(SYMBOL))
          .andExpect(jsonPath("$.details.clOrdId").value(secondClOrdId));

      releaseFirstOrder.countDown();
      firstOrder.get(10, TimeUnit.SECONDS);

      assertThat(accountCashBalance()).isEqualByComparingTo("107200000.0000");
      assertThat(positionQuantity()).isEqualByComparingTo("400.0000");
      assertThat(count("orders")).isEqualTo(1);
      assertThat(count("executions")).isEqualTo(1);
      assertThat(count("journal_entries")).isEqualTo(1);
      assertThat(count("ledger_entries")).isEqualTo(2);
      assertThat(count("ledger_entry_refs")).isEqualTo(2);

      mockMvc.perform(get("/actuator/prometheus"))
          .andExpect(status().isOk())
          .andExpect(content().string(org.hamcrest.Matchers.containsString("corebank_order_position_lock_wait_seconds_count 2.0")))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("corebank_order_position_lock_hold_seconds_count 1.0")))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("corebank_order_position_lock_conflicts_total 1.0")));

      verify(fepClient, times(1)).submitOrder(any(FepOutboundOrderPayload.class), anyString());
    } finally {
      releaseFirstOrder.countDown();
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
    return jdbcTemplate.queryForObject("SELECT cash_balance FROM accounts WHERE id = 1", BigDecimal.class);
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
