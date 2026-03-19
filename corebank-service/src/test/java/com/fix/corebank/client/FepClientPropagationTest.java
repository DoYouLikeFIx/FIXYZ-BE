package com.fix.corebank.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepOrdStatus;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class FepClientPropagationTest {

  private static final String CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174247";
  private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  private WireMockServer wireMockServer;
  private FepClient fepClient;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
    fepClient = new FepClient(
        RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl("http://127.0.0.1:" + wireMockServer.port())
            .build(),
        "test-internal-secret"
    );
  }

  @AfterEach
  void tearDown() {
    CorrelationIdSupport.clearMdc();
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void shouldForwardCorrelationIdAndClOrdIdOnSubmit() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(successfulSubmitBody())));

    FepOrderResult result = fepClient.submitOrder(buildSubmitPayload(), "trace-core-submit-001");

    assertThat(result.clOrdId()).isEqualTo(CL_ORD_ID);
    wireMockServer.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-core-submit-001"))
        .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(CL_ORD_ID)));
  }

  @Test
  void shouldForwardTraceparentOnSubmit() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(successfulSubmitBody())));

    TraceparentSupport.putInMdc(TRACEPARENT);

    fepClient.submitOrder(buildSubmitPayload(), "trace-core-submit-002");

    wireMockServer.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.TRACEPARENT, equalTo(TRACEPARENT)));
  }

  @Test
  void shouldForwardCorrelationIdAndTraceparentOnStatusQuery() {
    wireMockServer.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID)))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(successfulStatusBody())));

    TraceparentSupport.putInMdc(TRACEPARENT);

    FepOrderResult result = fepClient.queryOrderStatus(CL_ORD_ID, "trace-core-status-001");

    assertThat(result.clOrdId()).isEqualTo(CL_ORD_ID);
    assertThat(result.ordStatus()).isEqualTo(FepOrdStatus.PENDING);
    wireMockServer.verify(getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID)))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-core-status-001"))
        .withHeader(CommonHeaders.TRACEPARENT, equalTo(TRACEPARENT)));
  }

  private FepOutboundOrderPayload buildSubmitPayload() {
    return new FepOutboundOrderPayload(
        CL_ORD_ID,
        "ACC-001",
        "005930",
        com.fix.common.fep.FepSecurityExchange.KRX,
        com.fix.common.fep.FepSide.BUY,
        com.fix.common.fep.FepOrderType.LIMIT,
        2L,
        70100L,
        null,
        null,
        null,
        null,
        "KRW",
        "ref-core-propagation"
    );
  }

  private String successfulSubmitBody() {
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
        """.formatted(CL_ORD_ID, CL_ORD_ID);
  }

  private String successfulStatusBody() {
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
        """.formatted(CL_ORD_ID, CL_ORD_ID);
  }
}
