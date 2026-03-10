package com.fix.corebank.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
  private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    WIRE_MOCK_SERVER.start();
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private OrderRepository orderRepository;

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
    orderRepository.deleteAll();
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
