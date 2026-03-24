package com.fix.corebank.integration;

import static com.fix.corebank.support.CorebankLiquidityFixtures.seedRestingSellLiquidity;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.corebank.entity.Execution;
import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.ExecutionRepository;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.service.CorebankOrderService;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.CanonicalBookEntry;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.CanonicalMatchingScenario;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.ExpectedFill;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.ExpectedOutcome;
import com.fix.corebank.vo.InternalOrderCreateCommand;
import com.fix.corebank.vo.InternalOrderResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.groups.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret"
})
class CorebankMatchingPersistenceIntegrationTest extends CorebankContainersIntegrationTestBase {

  private static final long TAKER_ACCOUNT_ID = 1L;
  private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    WIRE_MOCK_SERVER.start();
  }

  @Autowired
  private CorebankOrderService corebankOrderService;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private ExecutionRepository executionRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

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
    WIRE_MOCK_SERVER.resetAll();
    jdbcTemplate.update("DELETE FROM ledger_entry_refs");
    jdbcTemplate.update("DELETE FROM ledger_entries");
    jdbcTemplate.update("DELETE FROM journal_entries");
    jdbcTemplate.update("DELETE FROM executions");
    jdbcTemplate.update("DELETE FROM orders");
    jdbcTemplate.update("DELETE FROM positions");
    jdbcTemplate.update(
        "UPDATE accounts SET status = 'ACTIVE', cash_balance = 1000000000.0000, daily_sell_limit = 1000000.0000 WHERE id = 1"
    );
    circuitBreakerRegistry.circuitBreaker("fep-submit").reset();
  }

  @Test
  void shouldPersistMarketSweepSummaryExecutionSequenceAndQuoteTraceConsistently() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.marketSweep();
    String clOrdId = UUID.randomUUID().toString();
    String quoteSnapshotId = "qsnap-" + clOrdId;
    Instant quoteAsOf = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(5);
    seedRestingSellBook(scenario);
    stubGatewayFill(clOrdId, scenario.expected(), "FEP-KRX-" + clOrdId, "FILLED");

    InternalOrderResult result = corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        TAKER_ACCOUNT_ID,
        clOrdId,
        scenario.symbol(),
        "BUY",
        "MARKET",
        scenario.orderQty(),
        null,
        quoteSnapshotId,
        quoteAsOf,
        FepQuoteSourceMode.LIVE,
        scenario.expected().weightedAvgPrice()
    ));

    assertThat(result.getStatus()).isEqualTo("FILLED");
    assertThat(result.getExecutionResult()).isEqualTo("FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo(scenario.expected().totalExecutedQty());
    assertThat(result.getLeavesQty()).isEqualByComparingTo(scenario.expected().leavesQty());
    assertThat(result.getExecutedPrice()).isEqualByComparingTo(scenario.expected().weightedAvgPrice());

    Order takerOrder = orderRepository.findByClOrdId(clOrdId).orElseThrow();
    assertOrderSummaryMatchesExpected(
        takerOrder,
        scenario.expected(),
        "FILLED",
        quoteSnapshotId,
        quoteAsOf,
        FepQuoteSourceMode.LIVE
    );
    assertExecutionsMatchExpected(
        executionRepository.findAllByOrderIdOrderByExecutionSeqAsc(takerOrder.getId()),
        scenario.expected(),
        quoteSnapshotId,
        quoteAsOf,
        FepQuoteSourceMode.LIVE
    );
    assertMakerOrdersReflectExpectedFills(scenario.expected().fills());
    assertThat(executionRepository.count()).isEqualTo(6);

    WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/fep/v1/orders")));
  }

  @Test
  void shouldPersistLimitCrossSummaryAndExecutionRowsWithoutQuoteTraceFields() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.limitCross();
    String clOrdId = UUID.randomUUID().toString();
    seedRestingSellBook(scenario);
    stubGatewayFill(clOrdId, scenario.expected(), "FEP-KRX-" + clOrdId, "FILLED");

    InternalOrderResult result = corebankOrderService.createOrder(InternalOrderCreateCommand.of(
        TAKER_ACCOUNT_ID,
        clOrdId,
        scenario.symbol(),
        scenario.side(),
        "LIMIT",
        scenario.orderQty(),
        scenario.limitPrice(),
        null,
        null,
        null,
        null
    ));

    assertThat(result.getStatus()).isEqualTo("FILLED");
    assertThat(result.getExecutionResult()).isEqualTo("FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo(scenario.expected().totalExecutedQty());
    assertThat(result.getLeavesQty()).isEqualByComparingTo(scenario.expected().leavesQty());
    assertThat(result.getExecutedPrice()).isEqualByComparingTo(scenario.expected().weightedAvgPrice());

    Order takerOrder = orderRepository.findByClOrdId(clOrdId).orElseThrow();
    assertOrderSummaryMatchesExpected(takerOrder, scenario.expected(), "FILLED", null, null, null);
    assertExecutionsMatchExpected(
        executionRepository.findAllByOrderIdOrderByExecutionSeqAsc(takerOrder.getId()),
        scenario.expected(),
        null,
        null,
        null
    );
    assertMakerOrdersReflectExpectedFills(scenario.expected().fills());
    assertThat(executionRepository.count()).isEqualTo(4);

    WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/fep/v1/orders")));
  }

  private void seedRestingSellBook(CanonicalMatchingScenario scenario) {
    for (CanonicalBookEntry entry : scenario.oppositeBook()) {
      seedRestingSellLiquidity(
          jdbcTemplate,
          orderRepository,
          entry.accountId(),
          entry.accountId(),
          accountNumber(entry.accountId()),
          entry.symbol(),
          entry.clOrdId(),
          entry.remainingQty(),
          entry.limitPrice()
      );
    }
  }

  private void assertMakerOrdersReflectExpectedFills(List<ExpectedFill> expectedFills) {
    for (ExpectedFill expectedFill : expectedFills) {
      Order makerOrder = orderRepository.findByClOrdId(expectedFill.makerClOrdId()).orElseThrow();
      String expectedStatus = expectedFill.remainingMakerQty().signum() == 0 ? "FILLED" : "PARTIALLY_FILLED";
      assertThat(makerOrder.getStatus()).isEqualTo(expectedStatus);
      assertThat(makerOrder.getExecutionResult()).isEqualTo(expectedStatus);
      assertThat(makerOrder.getExecutedQty()).isEqualByComparingTo(expectedFill.executedQty());
      assertThat(makerOrder.getLeavesQty()).isEqualByComparingTo(expectedFill.remainingMakerQty());
      assertThat(makerOrder.getExecutedPrice()).isEqualByComparingTo(expectedFill.executedPrice());
      assertThat(makerOrder.getQuoteSnapshotId()).isNull();
      assertThat(makerOrder.getQuoteAsOf()).isNull();
      assertThat(makerOrder.getQuoteSourceMode()).isNull();

      assertThat(executionRepository.findAllByOrderIdOrderByExecutionSeqAsc(makerOrder.getId()))
          .extracting(
              Execution::getExecutionSeq,
              Execution::getExecQty,
              Execution::getExecPrice,
              Execution::getQuoteSnapshotId,
              Execution::getQuoteAsOf,
              Execution::getQuoteSourceMode
          )
          .containsExactly(tuple(1, expectedFill.executedQty(), expectedFill.executedPrice(), null, null, null));
    }
  }

  private void assertOrderSummaryMatchesExpected(
      Order order,
      ExpectedOutcome expected,
      String expectedStatus,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode
  ) {
    assertThat(order.getStatus()).isEqualTo(expectedStatus);
    assertThat(order.getExecutionResult()).isEqualTo(expectedStatus);
    assertThat(order.getExecutedQty()).isEqualByComparingTo(expected.totalExecutedQty());
    assertThat(order.getLeavesQty()).isEqualByComparingTo(expected.leavesQty());
    assertThat(order.getExecutedPrice()).isEqualByComparingTo(expected.weightedAvgPrice());
    assertThat(order.getQuoteSnapshotId()).isEqualTo(quoteSnapshotId);
    assertThat(order.getQuoteAsOf()).isEqualTo(quoteAsOf);
    assertThat(order.getQuoteSourceMode()).isEqualTo(quoteSourceMode);
    assertThat(order.getExecutedAt()).isNotNull();
  }

  private void assertExecutionsMatchExpected(
      List<Execution> executions,
      ExpectedOutcome expected,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode
  ) {
    List<Tuple> expectedTuples = new ArrayList<>();
    IntStream.range(0, expected.fills().size())
        .forEach(index -> {
          ExpectedFill fill = expected.fills().get(index);
          expectedTuples.add(tuple(
              index + 1,
              fill.executedQty(),
              fill.executedPrice(),
              quoteSnapshotId,
              quoteAsOf,
              quoteSourceMode
          ));
        });

    assertThat(executions)
        .extracting(
            Execution::getExecutionSeq,
            Execution::getExecQty,
            Execution::getExecPrice,
            Execution::getQuoteSnapshotId,
            Execution::getQuoteAsOf,
            Execution::getQuoteSourceMode
        )
        .containsExactlyElementsOf(expectedTuples);
  }

  private void stubGatewayFill(
      String clOrdId,
      ExpectedOutcome expected,
      String fepOrderId,
      String ordStatus
  ) {
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
                """
                    {
                      "success": true,
                      "data": {
                        "clOrdId": "%s",
                        "fepOrderId": "%s",
                        "execType": "FILL",
                        "ordStatus": "%s",
                        "executedQty": %s,
                        "executedPrice": %s,
                        "leavesQty": %s,
                        "transactTime": "2026-03-01T10:05:30Z"
                      }
                    }
                    """.formatted(
                    clOrdId,
                    fepOrderId,
                    ordStatus,
                    expected.totalExecutedQty().toPlainString(),
                    expected.weightedAvgPrice().toPlainString(),
                    expected.leavesQty().toPlainString()
                ))));
  }

  private String accountNumber(Long accountId) {
    return String.valueOf(200000000000L + accountId);
  }
}
