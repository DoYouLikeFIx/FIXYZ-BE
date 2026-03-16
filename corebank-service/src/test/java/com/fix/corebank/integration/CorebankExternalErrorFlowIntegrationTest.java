package com.fix.corebank.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_external_error_flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "internal.secret=test-secret"
})
class CorebankExternalErrorFlowIntegrationTest {

  private static final String CL_ORD_ID_TIMEOUT = "123e4567-e89b-42d3-a456-426614174220";
  private static final String CL_ORD_ID_UNKNOWN = "123e4567-e89b-42d3-a456-426614174221";
  private static final String CL_ORD_ID_REQUERY = "123e4567-e89b-42d3-a456-426614174222";
  private static final String CL_ORD_ID_REQUERY_TIMEOUT = "123e4567-e89b-42d3-a456-426614174223";
  private static final String CL_ORD_ID_SUBMIT_AFTER_REQUERY_FAILURES = "123e4567-e89b-42d3-a456-426614174224";
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
    assertThat(persistedOrder.getStatus()).isEqualTo("PENDING");
    assertThat(persistedOrder.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_FAILED);
    assertThat(persistedOrder.getFailureReason()).isEqualTo("TIMEOUT");
  }

  @Test
  void shouldFallbackUnknownExternalCodeThroughInternalApi() throws Exception {
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
        case "9099" -> new FepExternalError("CORE-003", "CONCURRENCY_FAILURE");
        default -> new FepExternalError("FEP-999", "UNKNOWN_EXTERNAL_" + externalRc);
      };
    }
  }
}
