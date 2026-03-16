package com.fix.corebank.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.entity.Account;
import com.fix.corebank.entity.Order;
import com.fix.corebank.entity.Position;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.service.OrderPostingTransactionHook;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret"
})
class CorebankSameBankLedgerPostingIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final long ACCOUNT_ID = 1L;
  private static final String SYMBOL = "005930";
  private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    WIRE_MOCK_SERVER.start();
  }

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @MockBean
  private OrderPostingTransactionHook orderPostingTransactionHook;

  @DynamicPropertySource
  static void registerWireMockProperties(DynamicPropertyRegistry registry) {
    registry.add("fep.gateway.base-url", WIRE_MOCK_SERVER::baseUrl);
  }

  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK_SERVER.stop();
  }

  @BeforeEach
  void setUp() {
    reset(orderPostingTransactionHook);
    WIRE_MOCK_SERVER.resetAll();
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
    circuitBreakerRegistry.circuitBreaker("fep-submit").reset();
  }

  @Test
  void shouldPostSellExecutionAtomicallyAndReturnFilledSummary() {
    String clOrdId = UUID.randomUUID().toString();
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(successfulFillResponse(clOrdId, "FEP-KRX-" + clOrdId))));

    InternalOrderResult result = corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        clOrdId,
        SYMBOL,
        "SELL",
        new BigDecimal("10.0000"),
        new BigDecimal("72000.0000")
    ));

    assertThat(result.getClOrdId()).isEqualTo(clOrdId);
    assertThat(result.getStatus()).isEqualTo("FILLED");
    assertThat(result.getExecutionResult()).isEqualTo("FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo("10.0000");
    assertThat(result.getLeavesQty()).isEqualByComparingTo("0.0000");
    assertThat(result.getExecutedPrice()).isEqualByComparingTo("72000.0000");
    assertThat(result.getExternalOrderId()).isEqualTo("FEP-KRX-" + clOrdId);

    Order persistedOrder = orderRepository.findByClOrdId(clOrdId).orElseThrow();
    assertThat(persistedOrder.getStatus()).isEqualTo("FILLED");
    assertThat(persistedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_CONFIRMED);
    assertThat(persistedOrder.getExecutionResult()).isEqualTo("FILLED");
    assertThat(persistedOrder.getExecutedQty()).isEqualByComparingTo("10.0000");
    assertThat(persistedOrder.getLeavesQty()).isEqualByComparingTo("0.0000");
    assertThat(persistedOrder.getExecutedPrice()).isEqualByComparingTo("72000.0000");

    assertThat(accountCashBalance()).isEqualByComparingTo("100720000.0000");
    assertThat(positionQuantity()).isEqualByComparingTo("110.0000");
    assertThat(count("orders")).isEqualTo(1);
    assertThat(count("executions")).isEqualTo(1);
    assertThat(count("journal_entries")).isEqualTo(1);
    assertThat(count("ledger_entries")).isEqualTo(2);
    assertThat(count("ledger_entry_refs")).isEqualTo(2);

    WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/fep/v1/orders")));
  }

  @Test
  void shouldRollbackPostingWhenMidTransactionFailureOccurs() {
    String clOrdId = UUID.randomUUID().toString();
    doThrow(new IllegalStateException("simulated posting failure"))
        .when(orderPostingTransactionHook)
        .afterPostingMutation(any(Order.class), any(Account.class), any(Position.class));

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        clOrdId,
        SYMBOL,
        "SELL",
        new BigDecimal("10.0000"),
        new BigDecimal("72000.0000")
    )))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("simulated posting failure");

    assertThat(accountCashBalance()).isEqualByComparingTo("100000000.0000");
    assertThat(positionQuantity()).isEqualByComparingTo("120.0000");
    assertThat(count("orders")).isEqualTo(0);
    assertThat(count("executions")).isEqualTo(0);
    assertThat(count("journal_entries")).isEqualTo(0);
    assertThat(count("ledger_entries")).isEqualTo(0);
    assertThat(count("ledger_entry_refs")).isEqualTo(0);

    WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlEqualTo("/fep/v1/orders")));
  }

  @Test
  void shouldRejectInsufficientPositionWithoutMutatingLedgerState() {
    String clOrdId = UUID.randomUUID().toString();

    assertThatThrownBy(() -> corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        ACCOUNT_ID,
        clOrdId,
        SYMBOL,
        "SELL",
        new BigDecimal("200.0000"),
        new BigDecimal("72000.0000")
    )))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.ORD_INSUFFICIENT_POSITION);

    assertThat(accountCashBalance()).isEqualByComparingTo("100000000.0000");
    assertThat(positionQuantity()).isEqualByComparingTo("120.0000");
    assertThat(count("orders")).isEqualTo(0);
    assertThat(count("executions")).isEqualTo(0);
    assertThat(count("journal_entries")).isEqualTo(0);
    assertThat(count("ledger_entries")).isEqualTo(0);
    assertThat(count("ledger_entry_refs")).isEqualTo(0);

    WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlEqualTo("/fep/v1/orders")));
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

  private String successfulFillResponse(String clOrdId, String fepOrderId) {
    return """
        {
          "success": true,
          "data": {
            "clOrdId": "%s",
            "fepOrderId": "%s",
            "execType": "FILL",
            "ordStatus": "FILLED",
            "executedQty": 10,
            "executedPrice": 72000,
            "leavesQty": 0,
            "transactTime": "2026-03-01T10:05:30Z"
          }
        }
        """.formatted(clOrdId, fepOrderId);
  }
}
