package com.fix.corebank.integration;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_external_error_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
  "internal.secret=test-secret",
  "resilience4j.circuitbreaker.instances.fep-submit.waitDurationInOpenState=1s",
  "resilience4j.circuitbreaker.instances.fep-status.waitDurationInOpenState=1s",
  "recovery.status-query.max-attempts=2",
  "recovery.status-query.backoff-ms=0"
})
class CorebankExternalErrorFlowIntegrationTest {

  private static final String CL_ORD_ID_TIMEOUT = "123e4567-e89b-42d3-a456-426614174220";
  private static final String CL_ORD_ID_UNKNOWN = "123e4567-e89b-42d3-a456-426614174221";
  private static final String CL_ORD_ID_REQUERY = "123e4567-e89b-42d3-a456-426614174222";
  private static final String CL_ORD_ID_REQUERY_TIMEOUT = "123e4567-e89b-42d3-a456-426614174223";
  private static final String CL_ORD_ID_SUBMIT_AFTER_REQUERY_FAILURES = "123e4567-e89b-42d3-a456-426614174224";
  private static final String CL_ORD_ID_CB_FAIL_1 = "123e4567-e89b-42d3-a456-426614174240";
  private static final String CL_ORD_ID_CB_FAIL_2 = "123e4567-e89b-42d3-a456-426614174241";
  private static final String CL_ORD_ID_CB_FAIL_3 = "123e4567-e89b-42d3-a456-426614174242";
  private static final String CL_ORD_ID_CB_OPEN_CALL = "123e4567-e89b-42d3-a456-426614174243";
  private static final String CL_ORD_ID_HALF_OPEN_PROBE = "123e4567-e89b-42d3-a456-426614174244";
  private static final String CL_ORD_ID_HALF_OPEN_SECOND = "123e4567-e89b-42d3-a456-426614174245";
  private static final long OPEN_STATE_WAIT_MILLIS = 1_200L;
  private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    WIRE_MOCK_SERVER.start();
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
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
        "UPDATE accounts SET status = 'ACTIVE', cash_balance = 100000000.0000, daily_sell_limit = 500.0000 WHERE id = 1"
    );
    jdbcTemplate.update(
        """
            INSERT INTO positions (account_id, symbol, qty, avg_price, created_at, updated_at, version)
            VALUES (1, '005930', 120.0000, 70000.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """
    );
    circuitBreakerRegistry.circuitBreaker("fep-submit").reset();
    circuitBreakerRegistry.circuitBreaker("fep-status").reset();
  }

  @Test
  void shouldBlockOrderSubmissionWhenAccountIsFrozenBeforeCallingFep() throws Exception {
    jdbcTemplate.update("UPDATE accounts SET status = 'FROZEN' WHERE id = 1");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-status-frozen")
            .param("accountId", "1")
            .param("clOrdId", "123e4567-e89b-42d3-a456-426614174290")
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-status-frozen"))
        .andExpect(jsonPath("$.code").value("ORD-012"))
        .andExpect(jsonPath("$.path").value("/internal/v1/orders"));

    WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlEqualTo("/fep/v1/orders")));
  }

  @Test
  void shouldBlockOrderSubmissionWhenAccountIsClosedBeforeCallingFep() throws Exception {
    jdbcTemplate.update("UPDATE accounts SET status = 'CLOSED' WHERE id = 1");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-status-closed")
            .param("accountId", "1")
            .param("clOrdId", "123e4567-e89b-42d3-a456-426614174291")
            .param("symbol", "005930")
            .param("side", "SELL")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-status-closed"))
        .andExpect(jsonPath("$.code").value("ORD-012"))
        .andExpect(jsonPath("$.path").value("/internal/v1/orders"));

    WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlEqualTo("/fep/v1/orders")));
  }

  @Test
  void shouldTranslateMappedExternalGatewayTimeoutThroughInternalApi() throws Exception {
    seedRestingSellLiquidity(2L, 2L, "200000000002", "maker-timeout", "2.0000", "70100.0000");
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(504, "9004", "cancel acknowledgement timed out")));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-timeout")
            .param("accountId", "1")
            .param("clOrdId", CL_ORD_ID_TIMEOUT)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-timeout"))
        .andExpect(jsonPath("$.code").value("FEP-002"))
        .andExpect(jsonPath("$.message").value("Exchange connectivity timeout"))
        .andExpect(jsonPath("$.path").value("/internal/v1/orders"))
        .andExpect(jsonPath("$.correlationId").value("trace-core-timeout"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.timeout"))
        .andExpect(jsonPath("$.operatorCode").value("TIMEOUT"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());

    WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-core-timeout")));

    Order persistedOrder = orderRepository.findByClOrdId(CL_ORD_ID_TIMEOUT).orElseThrow();
    assertThat(persistedOrder.getStatus()).isEqualTo("FILLED");
    assertThat(persistedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(persistedOrder.getFailureReason()).isEqualTo("TIMEOUT");
    assertThat(persistedOrder.getExecutionResult()).isEqualTo("FILLED");
    assertThat(accountCashBalance()).isEqualByComparingTo("99859800.0000");
    assertThat(positionQuantity("005930")).isEqualByComparingTo("122.0000");
  }

  @Test
  void shouldFallbackUnknownExternalCodeThroughInternalApi() throws Exception {
    seedRestingSellLiquidity(2L, 2L, "200000000002", "maker-unknown", "2.0000", "70100.0000");
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(502, "9555", "unclassified upstream failure")));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .param("accountId", "1")
            .param("clOrdId", CL_ORD_ID_UNKNOWN)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("FEP-999"))
        .andExpect(jsonPath("$.message").value("Unknown external error"))
        .andExpect(jsonPath("$.path").value("/internal/v1/orders"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.unknown_external"))
        .andExpect(jsonPath("$.operatorCode").value("UNKNOWN_EXTERNAL_9555"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty());

    Order persistedOrder = orderRepository.findByClOrdId(CL_ORD_ID_UNKNOWN).orElseThrow();
    assertThat(persistedOrder.getStatus()).isEqualTo("FILLED");
    assertThat(persistedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_ESCALATED);
    assertThat(persistedOrder.getFailureReason()).isEqualTo("UNKNOWN_EXTERNAL_9555");
    assertThat(persistedOrder.getExecutionResult()).isEqualTo("FILLED");
    assertThat(accountCashBalance()).isEqualByComparingTo("99859800.0000");
    assertThat(positionQuantity("005930")).isEqualByComparingTo("122.0000");
  }

  @Test
  void shouldEscalateRejectedSubmitWhilePreservingCanonicalFill() throws Exception {
    String clOrdId = "123e4567-e89b-42d3-a456-426614174225";
    seedRestingSellLiquidity(2L, 2L, "200000000002", "maker-rejected", "2.0000", "70100.0000");
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(400, "9097", "order rejected by exchange")));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-rejected")
            .param("accountId", "1")
            .param("clOrdId", clOrdId)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isBadRequest())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-rejected"))
        .andExpect(jsonPath("$.code").value("FEP-003"))
        .andExpect(jsonPath("$.message").value("Exchange rejected order"))
        .andExpect(jsonPath("$.operatorCode").value("ORDER_REJECTED"));

    Order persistedOrder = orderRepository.findByClOrdId(clOrdId).orElseThrow();
    assertThat(persistedOrder.getStatus()).isEqualTo("FILLED");
    assertThat(persistedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_ESCALATED);
    assertThat(persistedOrder.getFailureReason()).isEqualTo("ORDER_REJECTED");
    assertThat(persistedOrder.getExecutionResult()).isEqualTo("FILLED");
    assertThat(accountCashBalance()).isEqualByComparingTo("99859800.0000");
    assertThat(positionQuantity("005930")).isEqualByComparingTo("122.0000");
  }

  @Test
  void shouldTranslateMappedExternalConcurrencyFailureThroughRequeryApi() throws Exception {
    orderRepository.saveAndFlush(Order.accepted(
        1L,
        CL_ORD_ID_REQUERY,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    ));

    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY)))
        .willReturn(canonicalGatewayError(409, "9099", "concurrency failure")));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/internal/v1/orders/{clOrdId}/requery",
            CL_ORD_ID_REQUERY
        )
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-requery"))
        .andExpect(status().isConflict())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-requery"))
        .andExpect(jsonPath("$.code").value("CORE-003"))
        .andExpect(jsonPath("$.message").value("Concurrent modification conflict"))
        .andExpect(jsonPath("$.path").value("/internal/v1/orders/%s/requery".formatted(CL_ORD_ID_REQUERY)))
        .andExpect(jsonPath("$.correlationId").value("trace-core-requery"))
        .andExpect(jsonPath("$.userMessageKey").value("error.core.concurrency_conflict"))
        .andExpect(jsonPath("$.operatorCode").value("CONCURRENCY_FAILURE"))
        .andExpect(jsonPath("$.timestamp").isNotEmpty());

    WIRE_MOCK_SERVER.verify(getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY)))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-core-requery")));
    WIRE_MOCK_SERVER.verify(1, getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY))));
  }

  @Test
  void shouldRetryStatusQueryOnceBeforeReturningSuccessfulRequery() throws Exception {
    Order order = Order.accepted(
        1L,
        CL_ORD_ID_REQUERY_TIMEOUT,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );
    order.updateStatus("PENDING");
    orderRepository.saveAndFlush(order);

    String retryScenario = "status-query-retry-success";
    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .inScenario(retryScenario)
        .whenScenarioStateIs(Scenario.STARTED)
        .willSetStateTo("second-attempt")
        .willReturn(canonicalGatewayError(504, "9004", "status timeout")));
    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .inScenario(retryScenario)
        .whenScenarioStateIs("second-attempt")
        .willReturn(successfulStatusResponse(CL_ORD_ID_REQUERY_TIMEOUT)));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/internal/v1/orders/{clOrdId}/requery",
            CL_ORD_ID_REQUERY_TIMEOUT
        )
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-retry-success")
            .param("attemptCount", "1"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-retry-success"))
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_REQUERY_TIMEOUT))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.externalSyncStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.message").value("pending at exchange"))
        .andExpect(jsonPath("$.data.retriable").value(true))
        .andExpect(jsonPath("$.data.escalationRequired").value(false))
        .andExpect(jsonPath("$.data.attemptCount").value(1))
        .andExpect(jsonPath("$.data.maxRetryCount").value(5));

    WIRE_MOCK_SERVER.verify(2, getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT))));
  }

  @Test
  void shouldPreserveTimeoutSemanticsWhenRetryLaterHitsUnavailable() throws Exception {
    Order order = Order.accepted(
        1L,
        CL_ORD_ID_REQUERY_TIMEOUT,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );
    order.updateStatus("UNKNOWN");
    orderRepository.saveAndFlush(order);

    String retryScenario = "status-query-timeout-then-unavailable";
    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .inScenario(retryScenario)
        .whenScenarioStateIs(Scenario.STARTED)
        .willSetStateTo("second-attempt")
        .willReturn(canonicalGatewayError(504, "9004", "status timeout")));
    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .inScenario(retryScenario)
        .whenScenarioStateIs("second-attempt")
        .willReturn(canonicalGatewayError(503, "9098", "status breaker open")));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/internal/v1/orders/{clOrdId}/requery",
            CL_ORD_ID_REQUERY_TIMEOUT
        )
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-timeout-then-unavailable")
            .param("attemptCount", "1"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-timeout-then-unavailable"))
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_REQUERY_TIMEOUT))
        .andExpect(jsonPath("$.data.status").value("UNKNOWN"))
        .andExpect(jsonPath("$.data.externalSyncStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.message").value("Exchange connectivity timeout"))
        .andExpect(jsonPath("$.data.retriable").value(true))
        .andExpect(jsonPath("$.data.escalationRequired").value(false))
        .andExpect(jsonPath("$.data.attemptCount").value(1))
        .andExpect(jsonPath("$.data.maxRetryCount").value(5));

    Order persistedOrder = orderRepository.findByClOrdId(CL_ORD_ID_REQUERY_TIMEOUT).orElseThrow();
    assertThat(persistedOrder.getFailureReason()).isEqualTo("TIMEOUT");
    WIRE_MOCK_SERVER.verify(2, getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT))));
  }

  @Test
  void shouldReturnRetriableClassificationForTransientRequeryTimeout() throws Exception {
    Order order = Order.accepted(
        1L,
        CL_ORD_ID_REQUERY_TIMEOUT,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );
    order.updateStatus("PENDING");
    orderRepository.saveAndFlush(order);

    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .willReturn(canonicalGatewayError(504, "9004", "status timeout")));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/internal/v1/orders/{clOrdId}/requery",
            CL_ORD_ID_REQUERY_TIMEOUT
        )
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-timeout")
            .param("attemptCount", "1"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-timeout"))
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_REQUERY_TIMEOUT))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.externalSyncStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.message").value("Exchange connectivity timeout"))
        .andExpect(jsonPath("$.data.retriable").value(true))
        .andExpect(jsonPath("$.data.escalationRequired").value(false))
        .andExpect(jsonPath("$.data.attemptCount").value(1))
        .andExpect(jsonPath("$.data.maxRetryCount").value(5));

    Order persistedOrder = orderRepository.findByClOrdId(CL_ORD_ID_REQUERY_TIMEOUT).orElseThrow();
    assertThat(persistedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(persistedOrder.getFailureReason()).isEqualTo("TIMEOUT");
    WIRE_MOCK_SERVER.verify(2, getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT))));
  }

  @Test
  void shouldPreserveCanonicalPendingStatusWhenRequeryReturnsRejected() throws Exception {
    Order order = Order.accepted(
        1L,
        CL_ORD_ID_REQUERY,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );
    order.completeExecution(
        "PENDING",
        "FILLED",
        new BigDecimal("2.0000"),
        BigDecimal.ZERO.setScale(4),
        new BigDecimal("70100.0000"),
        java.time.Instant.parse("2026-03-01T10:00:00Z")
    );
    orderRepository.saveAndFlush(order);

    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY)))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "fepOrderId": "FEP-KRX-%s",
                    "execType": "REJECTED",
                    "ordStatus": "REJECTED",
                    "queryTime": "2026-03-01T10:11:00Z",
                    "rejectReason": "INSUFFICIENT_FUNDS"
                  },
                  "error": null
                }
                """.formatted(CL_ORD_ID_REQUERY, CL_ORD_ID_REQUERY))));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/internal/v1/orders/{clOrdId}/requery",
            CL_ORD_ID_REQUERY
        )
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-rejected")
            .param("attemptCount", "2"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-rejected"))
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_REQUERY))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.externalSyncStatus").value("ESCALATED"))
        .andExpect(jsonPath("$.data.executionResult").value("FILLED"))
        .andExpect(jsonPath("$.data.executedQty").value(2.0))
        .andExpect(jsonPath("$.data.executedPrice").value(70100.0))
        .andExpect(jsonPath("$.data.externalOrderId").value("FEP-KRX-" + CL_ORD_ID_REQUERY))
        .andExpect(jsonPath("$.data.message").value("INSUFFICIENT_FUNDS"))
        .andExpect(jsonPath("$.data.retriable").value(false))
        .andExpect(jsonPath("$.data.escalationRequired").value(true));

    Order persistedOrder = orderRepository.findByClOrdId(CL_ORD_ID_REQUERY).orElseThrow();
    assertThat(persistedOrder.getStatus()).isEqualTo("PENDING");
    assertThat(persistedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_ESCALATED);
    assertThat(persistedOrder.getFepReferenceId()).isEqualTo("FEP-KRX-" + CL_ORD_ID_REQUERY);
    assertThat(persistedOrder.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
    assertThat(persistedOrder.getExecutionResult()).isEqualTo("FILLED");
  }

  @Test
  void shouldPreserveTerminalOrderWhenSeparateTransactionWinsDuringRequery() throws Exception {
    Order order = Order.accepted(
        1L,
        CL_ORD_ID_REQUERY_TIMEOUT,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );
    order.updateStatus("PENDING");
    orderRepository.saveAndFlush(order);

    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .willReturn(canonicalGatewayError(504, "9004", "status timeout")
            .withFixedDelay(1_500)));

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    try {
      Future<?> requeryCall = executorService.submit(() -> {
        try {
          mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                  "/internal/v1/orders/{clOrdId}/requery",
                  CL_ORD_ID_REQUERY_TIMEOUT
              )
                  .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
                  .header(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-concurrent-terminal")
                  .param("attemptCount", "1"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_REQUERY_TIMEOUT))
              .andExpect(jsonPath("$.data.status").value("FILLED"))
              .andExpect(jsonPath("$.data.externalSyncStatus").value("CONFIRMED"))
              .andExpect(jsonPath("$.data.message").value("Exchange connectivity timeout"))
              .andExpect(jsonPath("$.data.retriable").value(false))
              .andExpect(jsonPath("$.data.escalationRequired").value(false));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });

      waitForStatusRequestObserved(CL_ORD_ID_REQUERY_TIMEOUT, 3_000L);

      Order terminalOrder = orderRepository.findByClOrdId(CL_ORD_ID_REQUERY_TIMEOUT).orElseThrow();
      terminalOrder.updateState("FILLED", Order.EXTERNAL_SYNC_CONFIRMED, terminalOrder.getFepReferenceId(), null);
      orderRepository.saveAndFlush(terminalOrder);

      requeryCall.get(10, TimeUnit.SECONDS);
    } finally {
      executorService.shutdownNow();
      executorService.awaitTermination(5, TimeUnit.SECONDS);
    }

    Order persistedOrder = orderRepository.findByClOrdId(CL_ORD_ID_REQUERY_TIMEOUT).orElseThrow();
    assertThat(persistedOrder.getStatus()).isEqualTo("FILLED");
    assertThat(persistedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_CONFIRMED);
    assertThat(persistedOrder.getFailureReason()).isNull();
    WIRE_MOCK_SERVER.verify(2, getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT))));
  }

  @Test
  void shouldProduceEscalationSignalWhenTransientRequeryTimeoutHitsThreshold() throws Exception {
    Order order = Order.accepted(
        1L,
        CL_ORD_ID_REQUERY_TIMEOUT,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );
    order.updateStatus("UNKNOWN");
    orderRepository.saveAndFlush(order);

    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .willReturn(canonicalGatewayError(504, "9004", "status timeout")));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/internal/v1/orders/{clOrdId}/requery",
            CL_ORD_ID_REQUERY_TIMEOUT
        )
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-threshold")
            .param("attemptCount", "5"))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-threshold"))
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_REQUERY_TIMEOUT))
        .andExpect(jsonPath("$.data.status").value("UNKNOWN"))
        .andExpect(jsonPath("$.data.externalSyncStatus").value("ESCALATED"))
        .andExpect(jsonPath("$.data.retriable").value(false))
        .andExpect(jsonPath("$.data.escalationRequired").value(true))
        .andExpect(jsonPath("$.data.attemptCount").value(5))
        .andExpect(jsonPath("$.data.maxRetryCount").value(5));

    Order persistedOrder = orderRepository.findByClOrdId(CL_ORD_ID_REQUERY_TIMEOUT).orElseThrow();
    assertThat(persistedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_ESCALATED);
    assertThat(persistedOrder.getFailureReason()).isEqualTo("TIMEOUT");
    WIRE_MOCK_SERVER.verify(2, getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT))));
  }

  @Test
  void shouldKeepOrderSubmissionAvailableAfterStatusBreakerOpens() throws Exception {
    Order order = Order.accepted(
        1L,
        CL_ORD_ID_REQUERY_TIMEOUT,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );
    order.updateStatus("PENDING");
    orderRepository.saveAndFlush(order);

    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .willReturn(canonicalGatewayError(504, "9004", "status timeout")));

    for (int attempt = 1; attempt <= 3; attempt++) {
      mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
              "/internal/v1/orders/{clOrdId}/requery",
              CL_ORD_ID_REQUERY_TIMEOUT
          )
              .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
              .header(CommonHeaders.X_CORRELATION_ID, "trace-core-requery-open-" + attempt)
              .param("attemptCount", String.valueOf(attempt)))
          .andExpect(status().isOk());
    }

        assertCircuitState("fep-status", "OPEN");

    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(504, "9004", "submit timeout")));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-submit-after-status-open")
            .param("accountId", "1")
            .param("clOrdId", CL_ORD_ID_SUBMIT_AFTER_REQUERY_FAILURES)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-core-submit-after-status-open"))
        .andExpect(jsonPath("$.code").value("FEP-002"))
        .andExpect(jsonPath("$.message").value("Exchange connectivity timeout"))
        .andExpect(jsonPath("$.operatorCode").value("TIMEOUT"));

    WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-core-submit-after-status-open")));
  }

  @Test
  void shouldKeepSubmitCircuitClosedBeforeFailureThresholdReached() throws Exception {
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(504, "9004", "submit timeout")));

    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_1, "trace-core-threshold-fail-1");
    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_2, "trace-core-threshold-fail-2");
    assertCircuitState("fep-submit", "CLOSED");
    assertThat(circuitBreakerRegistry.circuitBreaker("fep-submit").getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    assertThat(circuitBreakerRegistry.circuitBreaker("fep-submit").getMetrics().getNumberOfBufferedCalls()).isEqualTo(2);

    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(CL_ORD_ID_CB_FAIL_3))
        .willReturn(successfulSubmitResponse(CL_ORD_ID_CB_FAIL_3)));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-threshold-success")
            .param("accountId", "1")
            .param("clOrdId", CL_ORD_ID_CB_FAIL_3)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_CB_FAIL_3));

    assertCircuitState("fep-submit", "CLOSED");
    WIRE_MOCK_SERVER.verify(3, postRequestedFor(urlEqualTo("/fep/v1/orders")));
  }

  @Test
  void shouldTransitionStatusBreakerFromOpenToClosedAfterSuccessfulProbe() throws Exception {
    Order order = Order.accepted(
        1L,
        CL_ORD_ID_REQUERY_TIMEOUT,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
      );
    order.updateStatus("PENDING");
    orderRepository.saveAndFlush(order);

    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .willReturn(canonicalGatewayError(504, "9004", "status timeout")));

    for (int attempt = 1; attempt <= 3; attempt++) {
      mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
              "/internal/v1/orders/{clOrdId}/requery",
              CL_ORD_ID_REQUERY_TIMEOUT
          )
              .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
              .header(CommonHeaders.X_CORRELATION_ID, "trace-core-status-open-" + attempt)
              .param("attemptCount", String.valueOf(attempt)))
          .andExpect(status().isOk());
    }

    assertCircuitState("fep-status", "OPEN");
    waitForOpenStateCooldown();

    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .willReturn(successfulStatusResponse(CL_ORD_ID_REQUERY_TIMEOUT)));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/internal/v1/orders/{clOrdId}/requery",
            CL_ORD_ID_REQUERY_TIMEOUT
        )
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-status-half-open-success")
            .param("attemptCount", "4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_REQUERY_TIMEOUT));

    assertCircuitState("fep-status", "CLOSED");
  }

  @Test
  void shouldReturnStatusBreakerToOpenWhenHalfOpenProbeFails() throws Exception {
    Order order = Order.accepted(
        1L,
        CL_ORD_ID_REQUERY_TIMEOUT,
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );
    order.updateStatus("PENDING");
    orderRepository.saveAndFlush(order);

    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .willReturn(canonicalGatewayError(504, "9004", "status timeout")));

    for (int attempt = 1; attempt <= 3; attempt++) {
      mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
              "/internal/v1/orders/{clOrdId}/requery",
              CL_ORD_ID_REQUERY_TIMEOUT
          )
              .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
              .header(CommonHeaders.X_CORRELATION_ID, "trace-core-status-open-fail-" + attempt)
              .param("attemptCount", String.valueOf(attempt)))
          .andExpect(status().isOk());
    }

    assertCircuitState("fep-status", "OPEN");
    waitForOpenStateCooldown();

    WIRE_MOCK_SERVER.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_REQUERY_TIMEOUT)))
        .willReturn(canonicalGatewayError(504, "9004", "status half-open probe timeout")));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/internal/v1/orders/{clOrdId}/requery",
            CL_ORD_ID_REQUERY_TIMEOUT
        )
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-status-half-open-failure")
            .param("attemptCount", "4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_REQUERY_TIMEOUT));

    assertCircuitState("fep-status", "OPEN");
  }

  @Test
  void shouldOpenCircuitAfterConsecutiveSubmitTimeoutsAndClassifyNextCallAsCircuitOpen() throws Exception {
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(504, "9004", "submit timeout")));

    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_1, "trace-core-cb-fail-1");
    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_2, "trace-core-cb-fail-2");
    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_3, "trace-core-cb-fail-3");

    assertThat(circuitBreakerRegistry.circuitBreaker("fep-submit").getState().name()).isEqualTo("OPEN");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-cb-open-call")
            .param("accountId", "1")
            .param("clOrdId", CL_ORD_ID_CB_OPEN_CALL)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("FEP-001"))
        .andExpect(jsonPath("$.operatorCode").value("CIRCUIT_OPEN"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.unavailable"));

    WIRE_MOCK_SERVER.verify(3, postRequestedFor(urlEqualTo("/fep/v1/orders")));
  }

  @Test
  void shouldTransitionFromHalfOpenToClosedWhenProbeSucceeds() throws Exception {
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(504, "9004", "submit timeout")));

    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_1, "trace-core-half-open-fail-1");
    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_2, "trace-core-half-open-fail-2");
    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_3, "trace-core-half-open-fail-3");
    assertCircuitState("fep-submit", "OPEN");

    waitForOpenStateCooldown();

    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(CL_ORD_ID_HALF_OPEN_PROBE))
        .willReturn(successfulSubmitResponse(CL_ORD_ID_HALF_OPEN_PROBE)));

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-half-open-probe-success")
            .param("accountId", "1")
            .param("clOrdId", CL_ORD_ID_HALF_OPEN_PROBE)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_HALF_OPEN_PROBE));

    assertCircuitState("fep-submit", "CLOSED");
  }

  @Test
  void shouldReturnToOpenWhenHalfOpenProbeFails() throws Exception {
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(504, "9004", "submit timeout")));

    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_1, "trace-core-half-open-return-fail-1");
    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_2, "trace-core-half-open-return-fail-2");
    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_3, "trace-core-half-open-return-fail-3");
    assertCircuitState("fep-submit", "OPEN");

    waitForOpenStateCooldown();

    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(CL_ORD_ID_HALF_OPEN_PROBE))
        .willReturn(canonicalGatewayError(504, "9004", "half-open probe timeout")));

    submitOrderExpectingTimeout(CL_ORD_ID_HALF_OPEN_PROBE, "trace-core-half-open-probe-failure");
    assertCircuitState("fep-submit", "OPEN");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-core-half-open-back-to-open")
            .param("accountId", "1")
            .param("clOrdId", CL_ORD_ID_HALF_OPEN_SECOND)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.operatorCode").value("CIRCUIT_OPEN"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.unavailable"));

    WIRE_MOCK_SERVER.verify(4, postRequestedFor(urlEqualTo("/fep/v1/orders")));
  }

  @Test
  void shouldAllowSingleProbeCallInHalfOpenState() throws Exception {
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(504, "9004", "submit timeout")));

    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_1, "trace-core-half-open-single-probe-1");
    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_2, "trace-core-half-open-single-probe-2");
    submitOrderExpectingTimeout(CL_ORD_ID_CB_FAIL_3, "trace-core-half-open-single-probe-3");
    assertCircuitState("fep-submit", "OPEN");

    waitForOpenStateCooldown();

    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(CL_ORD_ID_HALF_OPEN_PROBE))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withFixedDelay(1500)
            .withBody(successfulSubmitBody(CL_ORD_ID_HALF_OPEN_PROBE))));

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    try {
      Future<?> probeCall = executorService.submit(() -> {
        try {
          mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
                  .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
                  .header(CommonHeaders.X_CORRELATION_ID, "trace-core-half-open-first-probe")
                  .param("accountId", "1")
                  .param("clOrdId", CL_ORD_ID_HALF_OPEN_PROBE)
                  .param("symbol", "005930")
                  .param("side", "BUY")
                  .param("quantity", "2.0000")
                  .param("price", "70100.0000"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.data.clOrdId").value(CL_ORD_ID_HALF_OPEN_PROBE));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });

      waitForSubmitRequestObserved(CL_ORD_ID_HALF_OPEN_PROBE, 3_000L);

      mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
              .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
              .header(CommonHeaders.X_CORRELATION_ID, "trace-core-half-open-second-call")
              .param("accountId", "1")
              .param("clOrdId", CL_ORD_ID_HALF_OPEN_SECOND)
              .param("symbol", "005930")
              .param("side", "BUY")
              .param("quantity", "2.0000")
              .param("price", "70100.0000"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.operatorCode").value("CIRCUIT_OPEN"))
          .andExpect(jsonPath("$.userMessageKey").value("error.fep.unavailable"));

      probeCall.get(10, TimeUnit.SECONDS);
    } finally {
      executorService.shutdownNow();
      try {
        executorService.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
      }
    }

    WIRE_MOCK_SERVER.verify(4, postRequestedFor(urlEqualTo("/fep/v1/orders")));
    assertCircuitState("fep-submit", "CLOSED");
  }

  private void waitForOpenStateCooldown() throws InterruptedException {
    Thread.sleep(OPEN_STATE_WAIT_MILLIS);
  }

  private void waitForSubmitRequestObserved(String clOrdId, long timeoutMillis) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      try {
        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
            .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(clOrdId)));
        return;
      } catch (AssertionError ignored) {
        Thread.sleep(25);
      }
    }
    throw new AssertionError("Timed out waiting for submit request observation: " + clOrdId);
  }

  private void waitForStatusRequestObserved(String clOrdId, long timeoutMillis) throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (System.currentTimeMillis() < deadline) {
      try {
        WIRE_MOCK_SERVER.verify(getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(clOrdId))));
        return;
      } catch (AssertionError ignored) {
        Thread.sleep(25);
      }
    }
    throw new AssertionError("Timed out waiting for status request observation: " + clOrdId);
  }

  private void assertCircuitState(String breakerName, String expectedState) {
    assertThat(circuitBreakerRegistry.circuitBreaker(breakerName).getState().name()).isEqualTo(expectedState);
  }

  private void submitOrderExpectingTimeout(String clOrdId, String correlationId) throws Exception {
    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/v1/orders")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, correlationId)
            .param("accountId", "1")
            .param("clOrdId", clOrdId)
            .param("symbol", "005930")
            .param("side", "BUY")
            .param("quantity", "2.0000")
            .param("price", "70100.0000"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("FEP-002"))
        .andExpect(jsonPath("$.operatorCode").value("TIMEOUT"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.timeout"));
  }

  private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder successfulSubmitResponse(String clOrdId) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(successfulSubmitBody(clOrdId));
  }

  private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder successfulStatusResponse(String clOrdId) {
    return aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(successfulStatusBody(clOrdId));
  }

  private String successfulSubmitBody(String clOrdId) {
    return """
        {
          "success": true,
          "data": {
            "clOrdId": "%s",
            "fepOrderId": "FEP-KRX-%s",
            "execType": "PENDING_NEW",
            "ordStatus": "PENDING",
            "leavesQty": 2,
            "transactTime": "2026-03-01T10:00:00Z"
          },
          "error": null
        }
        """.formatted(clOrdId, clOrdId);
  }

  private String successfulStatusBody(String clOrdId) {
    return """
        {
          "success": true,
          "data": {
            "clOrdId": "%s",
            "fepOrderId": "FEP-KRX-%s",
            "ordStatus": "PENDING",
            "queryTime": "2026-03-01T10:10:00Z",
            "message": "pending at exchange"
          },
          "error": null
        }
        """.formatted(clOrdId, clOrdId);
  }

  private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder canonicalGatewayError(
      int httpStatus,
      String externalRc,
      String message
  ) {
    FepExternalError error = FepExternalError.from(externalRc);
    return aResponse()
        .withStatus(httpStatus)
        .withHeader("Content-Type", "application/json")
        .withBody("""
            {
              "success": false,
              "rc": "%s",
              "data": null,
              "error": {
                "code": "%s",
                "message": "%s",
                "rcDescription": "%s",
                "retryAfterSeconds": null
              },
              "traceId": "trace-%s"
            }
            """.formatted(externalRc, error.code, message, error.operatorCode, externalRc));
  }

  private record FepExternalError(String code, String operatorCode) {

    private static FepExternalError from(String externalRc) {
      return switch (externalRc) {
        case "9004" -> new FepExternalError("FEP-002", "TIMEOUT");
        case "9098" -> new FepExternalError("FEP-001", "CIRCUIT_OPEN");
        case "9099" -> new FepExternalError("CORE-003", "CONCURRENCY_FAILURE");
        default -> new FepExternalError("FEP-999", "UNKNOWN_EXTERNAL_" + externalRc);
      };
    }
  }

  private void seedRestingSellLiquidity(
      Long accountId,
      Long memberId,
      String accountNo,
      String clOrdId,
      String quantity,
      String price
  ) {
    Integer existingMemberCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM member WHERE id = ?",
        Integer.class,
        memberId
    );
    if (existingMemberCount != null && existingMemberCount == 0) {
      jdbcTemplate.update(
          """
              INSERT INTO member (id, member_no, email, created_at)
              VALUES (?, ?, ?, CURRENT_TIMESTAMP)
              """,
          memberId,
          "M-%05d".formatted(memberId),
          "member-%d@fix.test".formatted(memberId)
      );
    }

    Integer existingAccountCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM accounts WHERE id = ?",
        Integer.class,
        accountId
    );
    if (existingAccountCount != null && existingAccountCount == 0) {
      jdbcTemplate.update(
          """
              INSERT INTO accounts (
                id,
                account_no,
                currency,
                cash_balance,
                daily_sell_limit,
                member_id,
                status,
                created_at,
                updated_at,
                version
              )
              VALUES (?, ?, 'KRW', 100000000.0000, 500.0000, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
              """,
          accountId,
          accountNo,
          memberId
      );
    } else {
      jdbcTemplate.update(
          """
              UPDATE accounts
                 SET status = 'ACTIVE',
                     cash_balance = 100000000.0000,
                     daily_sell_limit = 500.0000
               WHERE id = ?
              """,
          accountId
      );
    }

    jdbcTemplate.update(
        """
            INSERT INTO positions (account_id, symbol, qty, avg_price, created_at, updated_at, version)
            VALUES (?, '005930', 10.0000, 69000.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            """,
        accountId
    );
    orderRepository.saveAndFlush(Order.accepted(
        accountId,
        clOrdId,
        "005930",
        "SELL",
        new BigDecimal(quantity),
        new BigDecimal(price)
    ));
  }

  private BigDecimal accountCashBalance() {
    return jdbcTemplate.queryForObject("SELECT cash_balance FROM accounts WHERE id = 1", BigDecimal.class);
  }

  private BigDecimal positionQuantity(String symbol) {
    return jdbcTemplate.queryForObject(
        "SELECT qty FROM positions WHERE account_id = 1 AND symbol = ?",
        BigDecimal.class,
        symbol
    );
  }
}
