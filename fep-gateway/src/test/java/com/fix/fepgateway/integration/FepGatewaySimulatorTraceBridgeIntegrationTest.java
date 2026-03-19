package com.fix.fepgateway.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fix.common.web.CommonHeaders;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FepGatewaySimulatorTraceBridgeIntegrationTest {

  private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
  private static final WireMockServer wireMockServer =
      new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

  @Autowired
  private MockMvc mockMvc;

  @BeforeAll
  static void startWireMock() {
    wireMockServer.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMockServer.stop();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    if (!wireMockServer.isRunning()) {
      wireMockServer.start();
    }
    registry.add("internal.secret", () -> "test-secret");
    registry.add(
        "fep.simulator.diagnostics-base-url",
        () -> "http://127.0.0.1:" + wireMockServer.port()
    );
  }

  @BeforeEach
  void resetWireMock() {
    wireMockServer.resetAll();
  }

  @Test
  void shouldForwardCorrelationIdAndTraceparentFromGatewayDiagnosticEndpoint() throws Exception {
    wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/fep-internal/v1/ping"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"service\":\"fep-simulator\",\"boundary\":\"open\"}")));

    mockMvc.perform(get("/fep-internal/v1/diagnostics/trace-forwarding/simulator")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-simulator-diagnostic-001")
            .header(CommonHeaders.TRACEPARENT, TRACEPARENT)
        )
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-simulator-diagnostic-001"))
        .andExpect(header().string(CommonHeaders.TRACEPARENT, TRACEPARENT))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.forwarded").value(true))
        .andExpect(jsonPath("$.data.correlationId").value("trace-simulator-diagnostic-001"))
        .andExpect(jsonPath("$.data.traceparent").value(TRACEPARENT));

    wireMockServer.verify(getRequestedFor(urlEqualTo("/fep-internal/v1/ping"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-simulator-diagnostic-001"))
        .withHeader(CommonHeaders.TRACEPARENT, equalTo(TRACEPARENT)));
  }

  @Test
  void shouldReturnDegradedDiagnosticResultWhenSimulatorBridgeFails() throws Exception {
    wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/fep-internal/v1/ping"))
        .willReturn(aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"code\":\"SIM-500\",\"message\":\"diagnostic failed\"}")));

    mockMvc.perform(get("/fep-internal/v1/diagnostics/trace-forwarding/simulator")
            .header(CommonHeaders.X_INTERNAL_SECRET, "test-secret")
            .header(CommonHeaders.X_CORRELATION_ID, "trace-simulator-diagnostic-failure")
            .header(CommonHeaders.TRACEPARENT, TRACEPARENT))
        .andExpect(status().isOk())
        .andExpect(header().string(CommonHeaders.X_CORRELATION_ID, "trace-simulator-diagnostic-failure"))
        .andExpect(header().string(CommonHeaders.TRACEPARENT, TRACEPARENT))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.forwarded").value(false))
        .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("500")));

    wireMockServer.verify(getRequestedFor(urlEqualTo("/fep-internal/v1/ping"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-simulator-diagnostic-failure"))
        .withHeader(CommonHeaders.TRACEPARENT, equalTo(TRACEPARENT)));
  }
}
