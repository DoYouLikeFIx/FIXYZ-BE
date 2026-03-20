package com.fix.fepgateway.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "fep.simulator.chaos-probe-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FepGatewayOrderErrorContractTest {

  private static final String SUBMIT_CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174260";
  private static final long SUBMIT_QTY = 10L;
  private static final long SUBMIT_PRICE = 72_000L;
  private static final long MATCH_AMOUNT = SUBMIT_QTY * SUBMIT_PRICE;

  private static final WireMockServer WIRE_MOCK_SERVER = new WireMockServer(wireMockConfig().dynamicPort());

  @Autowired
  private MockMvc mockMvc;

  @BeforeAll
  static void startWireMock() {
    WIRE_MOCK_SERVER.start();
  }

  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK_SERVER.stop();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("fep.simulator.base-url", WIRE_MOCK_SERVER::baseUrl);
  }

  @BeforeEach
  void setUp() {
    WIRE_MOCK_SERVER.resetAll();
    WIRE_MOCK_SERVER.stubFor(get(urlPathEqualTo("/api/v1/ping"))
        .withQueryParam("symbol", equalTo("005930"))
        .withQueryParam("exchange", equalTo("KRX"))
        .withQueryParam("amount", equalTo(Long.toString(MATCH_AMOUNT)))
        .willReturn(okJson("""
            {
              "service": "fep-simulator",
              "status": "ok",
              "chaosAction": "TIMEOUT"
            }
            """)));
  }

  @Test
  void shouldReturnTimeoutEnvelopeWhenSubmitAcknowledgementTimesOut() throws Exception {
    mockMvc.perform(post("/fep/v1/orders")
            .contentType(APPLICATION_JSON)
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-submit-timeout-001")
            .header(CommonHeaders.X_CL_ORD_ID, SUBMIT_CL_ORD_ID)
            .content("""
                {
                  "clOrdId": "%s",
                  "accountId": "ACC-001",
                  "symbol": "005930",
                  "securityExchange": "KRX",
                  "side": "BUY",
                  "orderType": "LIMIT",
                  "qty": %d,
                  "price": %d,
                  "currency": "KRW",
                  "referenceId": "ref-submit-timeout-001"
                }
                """.formatted(SUBMIT_CL_ORD_ID, SUBMIT_QTY, SUBMIT_PRICE)))
        .andExpect(status().isGatewayTimeout())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-submit-timeout-001"))
        .andExpect(jsonPath("$.code").value("9004"))
        .andExpect(jsonPath("$.message").value("submit acknowledgement timed out"))
        .andExpect(jsonPath("$.path").value("/fep/v1/orders"))
        .andExpect(jsonPath("$.correlationId").value("trace-submit-timeout-001"))
        .andExpect(jsonPath("$.userMessageKey").value("error.fep.timeout"))
        .andExpect(jsonPath("$.operatorCode").value("TIMEOUT"));
  }
}
