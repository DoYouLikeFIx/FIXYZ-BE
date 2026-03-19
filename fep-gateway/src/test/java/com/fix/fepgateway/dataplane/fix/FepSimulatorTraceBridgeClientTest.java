package com.fix.fepgateway.dataplane.fix;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

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

  @AfterEach
  void clearContext() {
    CorrelationIdSupport.clearMdc();
    MDC.remove(TraceparentSupport.MDC_KEY);
  }

  @Test
  void shouldForwardCorrelationIdAndTraceparentToSimulator() {
    wireMockServer.stubFor(get(urlEqualTo("/fep-internal/v1/ping"))
        .willReturn(aResponse().withStatus(200)));

    CorrelationIdSupport.putInMdc("trace-simulator-unit-001");
    TraceparentSupport.putInMdc(TRACEPARENT);

    FepSimulatorTraceBridgeClient client = new FepSimulatorTraceBridgeClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret",
        true
    );

    client.bridgeCurrentTrace();

    wireMockServer.verify(getRequestedFor(urlEqualTo("/fep-internal/v1/ping"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-simulator-unit-001"))
        .withHeader(CommonHeaders.TRACEPARENT, equalTo(TRACEPARENT)));
  }

  @Test
  void shouldSkipBridgeWhenDisabled() {
    FepSimulatorTraceBridgeClient client = new FepSimulatorTraceBridgeClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret",
        false
    );

    client.bridgeCurrentTrace();

    wireMockServer.verify(0, getRequestedFor(urlEqualTo("/fep-internal/v1/ping")));
  }

  @Test
  void shouldSwallowBridgeFailureForBestEffortSemantics() {
    wireMockServer.stubFor(get(urlEqualTo("/fep-internal/v1/ping"))
        .willReturn(aResponse().withStatus(500)));

    CorrelationIdSupport.putInMdc("trace-simulator-unit-002");
    TraceparentSupport.putInMdc(TRACEPARENT);

    FepSimulatorTraceBridgeClient client = new FepSimulatorTraceBridgeClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret",
        true
    );

    assertThatCode(client::bridgeCurrentTrace).doesNotThrowAnyException();

    wireMockServer.verify(1, getRequestedFor(urlEqualTo("/fep-internal/v1/ping")));
  }
}
