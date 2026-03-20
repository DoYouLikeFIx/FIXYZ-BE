package com.fix.corebank.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class FepQuoteSnapshotClientTest {

  private static final WireMockServer WIRE_MOCK_SERVER =
      new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
  private static final String TRACEPARENT =
      "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  private FepQuoteSnapshotClient client;

  @BeforeAll
  static void startWireMock() {
    WIRE_MOCK_SERVER.start();
  }

  @AfterAll
  static void stopWireMock() {
    WIRE_MOCK_SERVER.stop();
  }

  @BeforeEach
  void setUp() {
    WIRE_MOCK_SERVER.resetAll();
    client = new FepQuoteSnapshotClient(
        RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl("http://127.0.0.1:" + WIRE_MOCK_SERVER.port())
            .build(),
        "test-internal-secret"
    );
  }

  @AfterEach
  void tearDown() {
    CorrelationIdSupport.clearMdc();
  }

  @Test
  void shouldQueryLatestQuoteSnapshotAndForwardHeaders() {
    WIRE_MOCK_SERVER.stubFor(get(urlPathEqualTo("/fep-internal/v1/quotes/snapshots/latest"))
        .withQueryParam("symbol", equalTo("005930"))
        .withQueryParam("quoteSourceMode", equalTo("LIVE"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(successBody())));

    TraceparentSupport.putInMdc(TRACEPARENT);

    FepQuoteSnapshotResult result =
        client.queryLatestQuoteSnapshot("005930", FepQuoteSourceMode.LIVE, "trace-core-quote-001");

    assertThat(result.quoteSnapshotId()).isEqualTo("qsnap-005930-live-001");
    assertThat(result.symbol()).isEqualTo("005930");
    assertThat(result.quoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
    assertThat(result.quoteAsOf()).hasToString("2026-03-20T00:00:05Z");
    assertThat(result.bestBid()).isEqualTo(72000L);
    assertThat(result.bestAsk()).isEqualTo(72100L);
    assertThat(result.lastTrade()).isEqualTo(72050L);
    assertThat(result.streamOffset()).isEqualTo(42L);
    assertThat(result.stale()).isFalse();

    WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo("/fep-internal/v1/quotes/snapshots/latest"))
        .withQueryParam("symbol", equalTo("005930"))
        .withQueryParam("quoteSourceMode", equalTo("LIVE"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-core-quote-001"))
        .withHeader(CommonHeaders.TRACEPARENT, equalTo(TRACEPARENT)));
  }

  @Test
  void shouldTranslateNotFoundFromGateway() {
    WIRE_MOCK_SERVER.stubFor(get(urlPathEqualTo("/fep-internal/v1/quotes/snapshots/latest"))
        .withQueryParam("symbol", equalTo("005930"))
        .withQueryParam("quoteSourceMode", equalTo("LIVE"))
        .willReturn(aResponse()
            .withStatus(404)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "NOT_FOUND",
                  "message": "quote snapshot not found"
                }
                """)));

    assertThatThrownBy(() -> client.queryLatestQuoteSnapshot(
        "005930",
        FepQuoteSourceMode.LIVE,
        "trace-core-quote-not-found"
    ))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
          assertThat(ex.getMessage()).isEqualTo("quote snapshot not found");
        });
  }

  @Test
  void shouldTranslateGatewayTimeout() {
    WIRE_MOCK_SERVER.stubFor(get(urlPathEqualTo("/fep-internal/v1/quotes/snapshots/latest"))
        .withQueryParam("symbol", equalTo("005930"))
        .withQueryParam("quoteSourceMode", equalTo("LIVE"))
        .willReturn(aResponse()
            .withStatus(504)
            .withHeader("Content-Type", "application/json")));

    assertThatThrownBy(() -> client.queryLatestQuoteSnapshot(
        "005930",
        FepQuoteSourceMode.LIVE,
        "trace-core-quote-timeout"
    ))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FEP_GATEWAY_TIMEOUT);
          assertThat(ex.getMessage()).isEqualTo(ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage());
        });
  }

  @Test
  void shouldQueryLatestQuoteSnapshotBatchAndForwardHeaders() {
    WIRE_MOCK_SERVER.stubFor(get(urlPathEqualTo("/fep-internal/v1/quotes/snapshots/latest/batch"))
        .withQueryParam("symbol", equalTo("005930"))
        .withQueryParam("symbol", equalTo("000660"))
        .withQueryParam("quoteSourceMode", equalTo("LIVE"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(successBatchBody())));

    TraceparentSupport.putInMdc(TRACEPARENT);

    Map<String, FepQuoteSnapshotResult> result = client.queryLatestQuoteSnapshots(
        List.of("005930", "000660"),
        FepQuoteSourceMode.LIVE,
        "trace-core-quote-batch-001"
    );

    assertThat(result).containsOnlyKeys("005930", "000660");
    assertThat(result.get("005930").quoteSnapshotId()).isEqualTo("qsnap-005930-live-001");
    assertThat(result.get("000660").quoteSnapshotId()).isEqualTo("qsnap-000660-live-001");

    WIRE_MOCK_SERVER.verify(getRequestedFor(urlPathEqualTo("/fep-internal/v1/quotes/snapshots/latest/batch"))
        .withQueryParam("symbol", equalTo("005930"))
        .withQueryParam("symbol", equalTo("000660"))
        .withQueryParam("quoteSourceMode", equalTo("LIVE"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-core-quote-batch-001"))
        .withHeader(CommonHeaders.TRACEPARENT, equalTo(TRACEPARENT)));
  }

  @Test
  void shouldRejectBlankSymbolBeforeSendingRequest() {
    assertThatThrownBy(() -> client.queryLatestQuoteSnapshot(" ", FepQuoteSourceMode.LIVE, "trace-core-invalid"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);
          assertThat(ex.getMessage()).isEqualTo("symbol is required");
        });
  }

  @Test
  void shouldRejectEmptyBatchSymbolsBeforeSendingRequest() {
    assertThatThrownBy(() -> client.queryLatestQuoteSnapshots(List.of(), FepQuoteSourceMode.LIVE, "trace-core-invalid-batch"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);
          assertThat(ex.getMessage()).isEqualTo("symbol is required");
        });
  }

  private String successBody() {
    return """
        {
          "success": true,
          "data": {
            "quoteSnapshotId": "qsnap-005930-live-001",
            "symbol": "005930",
            "quoteSourceMode": "LIVE",
            "quoteAsOf": "2026-03-20T00:00:05Z",
            "bestBid": 72000,
            "bestAsk": 72100,
            "lastTrade": 72050,
            "streamOffset": 42,
            "stale": false
          },
          "error": null
        }
        """;
  }

  private String successBatchBody() {
    return """
        {
          "success": true,
          "data": [
            {
              "quoteSnapshotId": "qsnap-005930-live-001",
              "symbol": "005930",
              "quoteSourceMode": "LIVE",
              "quoteAsOf": "2026-03-20T00:00:05Z",
              "bestBid": 72000,
              "bestAsk": 72100,
              "lastTrade": 72050,
              "streamOffset": 42,
              "stale": false
            },
            {
              "quoteSnapshotId": "qsnap-000660-live-001",
              "symbol": "000660",
              "quoteSourceMode": "LIVE",
              "quoteAsOf": "2026-03-20T00:00:05Z",
              "bestBid": 120000,
              "bestAsk": 120500,
              "lastTrade": 120250,
              "streamOffset": 43,
              "stale": false
            }
          ],
          "error": null
        }
        """;
  }
}
