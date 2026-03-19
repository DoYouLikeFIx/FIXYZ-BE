package com.fix.fepgateway.dataplane.fix;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.web.CommonHeaders;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class FepSimulatorTraceBridgeClientTest {

  private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
  private static final WireMockServer wireMockServer =
      new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

  @BeforeAll
  static void startWireMock() {
    wireMockServer.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMockServer.stop();
  }

  @BeforeEach
  void resetWireMock() {
    wireMockServer.resetAll();
  }

  @Test
  void shouldForwardCorrelationIdAndTraceparentToSimulator() {
    wireMockServer.stubFor(get(urlEqualTo("/fep-internal/v1/ping"))
        .willReturn(aResponse().withStatus(200)));

    FepSimulatorTraceBridgeClient client = new FepSimulatorTraceBridgeClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret"
    );

    FepSimulatorTraceBridgeClient.TraceBridgeResult result =
        client.bridgeTrace("trace-simulator-unit-001", TRACEPARENT);

    assertThat(result.forwarded()).isTrue();
    wireMockServer.verify(getRequestedFor(urlEqualTo("/fep-internal/v1/ping"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-simulator-unit-001"))
        .withHeader(CommonHeaders.TRACEPARENT, equalTo(TRACEPARENT)));
  }

  @Test
  void shouldLogSuccessfulBridgeForwarding(CapturedOutput output) {
    wireMockServer.stubFor(get(urlEqualTo("/fep-internal/v1/ping"))
        .willReturn(aResponse().withStatus(200)));

    FepSimulatorTraceBridgeClient client = new FepSimulatorTraceBridgeClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret"
    );

    client.bridgeTrace("trace-simulator-unit-logging", TRACEPARENT);

    assertThat(output.getOut())
        .contains("operation=SIMULATOR_TRACE_DIAGNOSTIC")
        .contains("correlationId=trace-simulator-unit-logging")
        .contains("traceparent=" + TRACEPARENT)
        .contains("result=forwarded");
  }

  @Test
  void shouldRejectMissingDiagnosticTraceHeadersWithoutCallingSimulator() {
    FepSimulatorTraceBridgeClient client = new FepSimulatorTraceBridgeClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret"
    );

    FepSimulatorTraceBridgeClient.TraceBridgeResult result = client.bridgeTrace("", "bad-traceparent");

    assertThat(result.forwarded()).isFalse();
    assertThat(result.message()).contains("missing or invalid");
    wireMockServer.verify(0, getRequestedFor(urlEqualTo("/fep-internal/v1/ping")));
  }

  @Test
  void shouldReturnDiagnosticFailureAndLogWarningWhenSimulatorFails(CapturedOutput output) {
    wireMockServer.stubFor(get(urlEqualTo("/fep-internal/v1/ping"))
        .willReturn(aResponse().withStatus(500)));

    FepSimulatorTraceBridgeClient client = new FepSimulatorTraceBridgeClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret"
    );

    FepSimulatorTraceBridgeClient.TraceBridgeResult result =
        client.bridgeTrace("trace-simulator-unit-002", TRACEPARENT);

    assertThat(result.forwarded()).isFalse();
    assertThat(result.message()).contains("500");
    wireMockServer.verify(1, getRequestedFor(urlEqualTo("/fep-internal/v1/ping")));
    assertThat(output.getOut())
        .contains("operation=SIMULATOR_TRACE_DIAGNOSTIC")
        .contains("correlationId=trace-simulator-unit-002")
        .contains("traceparent=" + TRACEPARENT)
        .contains("500");
  }
}
