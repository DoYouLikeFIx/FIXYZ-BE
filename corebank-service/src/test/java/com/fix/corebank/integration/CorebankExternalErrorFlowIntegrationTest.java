package com.fix.corebank.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.github.tomakehurst.wiremock.WireMockServer;
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
  private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    WIRE_MOCK_SERVER.start();
  }

  @Autowired
  private MockMvc mockMvc;

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
  }

  @Test
  void shouldTranslateMappedExternalGatewayTimeoutThroughInternalApi() throws Exception {
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withStatus(504)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "9004",
                  "message": "cancel acknowledgement timed out",
                  "path": "/fep/v1/orders",
                  "correlationId": "trace-fep-002"
                }
                """)));

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
        .withHeader(CommonHeaders.X_CORRELATION_ID, matching("corebank-submit-" + CL_ORD_ID_TIMEOUT + "-.+")));
  }

  @Test
  void shouldFallbackUnknownExternalCodeThroughInternalApi() throws Exception {
    WIRE_MOCK_SERVER.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "9099",
                  "message": "concurrency failure",
                  "path": "/fep/v1/orders",
                  "correlationId": "trace-fep-999"
                }
                """)));

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
        .andExpect(jsonPath("$.operatorCode").value("UNKNOWN_EXTERNAL_9099"))
        .andExpect(jsonPath("$.correlationId").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }
}
