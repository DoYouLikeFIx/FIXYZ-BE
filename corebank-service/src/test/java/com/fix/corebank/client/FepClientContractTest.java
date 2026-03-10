package com.fix.corebank.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import com.fix.common.web.CommonHeaders;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class FepClientContractTest {

  private static final String CL_ORD_ID_1 = "123e4567-e89b-42d3-a456-426614174201";
  private static final String CL_ORD_ID_2 = "123e4567-e89b-42d3-a456-426614174202";
  private static final String CL_ORD_ID_3 = "123e4567-e89b-42d3-a456-426614174203";
  private static final String CL_ORD_ID_4 = "123e4567-e89b-42d3-a456-426614174204";
  private static final String CL_ORD_ID_5 = "123e4567-e89b-42d3-a456-426614174205";

  private WireMockServer wireMockServer;
  private FepClient fepClient;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(0);
    wireMockServer.start();
    fepClient = new FepClient(
        RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl(wireMockServer.baseUrl())
            .build(),
        "test-internal-secret"
    );
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void shouldValidateRequiredFieldsBeforeSendingRequest() {
    assertThatThrownBy(() -> new FepOutboundOrderPayload(
        CL_ORD_ID_1,
        "ACC-001",
        "",
        FepSecurityExchange.KRX,
        FepSide.BUY,
        FepOrderType.LIMIT,
        10L,
        72000L,
        null,
        null,
        null,
        null,
        "KRW",
        "ref-client-001"
    ))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("symbol");

    wireMockServer.verify(0, postRequestedFor(urlEqualTo("/fep/v1/orders")));
  }

  @Test
  void shouldSendVersionedGatewayRequestAndParseExplicitStatuses() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
                    .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "fepOrderId": "FEP-KRX-%s",
                    "execType": "FILL",
                    "ordStatus": "FILLED",
                    "executedQty": 10,
                    "executedPrice": 72000,
                    "leavesQty": 0,
                    "transactTime": "2026-03-01T10:05:30Z"
                  },
                  "error": null
                }
                """.formatted(CL_ORD_ID_2, CL_ORD_ID_2))));

    FepOutboundOrderPayload payload = new FepOutboundOrderPayload(
        CL_ORD_ID_2,
        "ACC-001",
        "005930",
        FepSecurityExchange.KRX,
        FepSide.BUY,
        FepOrderType.LIMIT,
        10L,
        72000L,
        null,
        null,
        null,
        null,
        "KRW",
        "ref-client-002"
    );

    FepOrderResult result = fepClient.submitOrder(payload, "trace-client-002");

    assertThat(result.clOrdId()).isEqualTo(CL_ORD_ID_2);
    assertThat(result.execType()).isEqualTo(FepExecType.FILL);
    assertThat(result.ordStatus()).isEqualTo(FepOrdStatus.FILLED);
    assertThat(result.executedQty()).isEqualTo(10L);
    assertThat(result.executedPrice()).isEqualTo(72000L);

    wireMockServer.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-client-002"))
        .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(CL_ORD_ID_2))
        .withRequestBody(equalToJson("""
            {
              "clOrdId": "%s",
              "accountId": "ACC-001",
              "symbol": "005930",
              "securityExchange": "KRX",
              "side": "BUY",
              "orderType": "LIMIT",
              "qty": 10,
              "price": 72000,
              "currency": "KRW",
              "referenceId": "ref-client-002"
            }
            """.formatted(CL_ORD_ID_2), true, true)));
  }

  @Test
  void shouldOmitPriceFieldForMarketOrders() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
                    .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "fepOrderId": "FEP-KRX-%s",
                    "execType": "PENDING_NEW",
                    "ordStatus": "PENDING",
                    "executedQty": 0,
                    "leavesQty": 10,
                    "transactTime": "2026-03-01T10:05:30Z"
                  },
                  "error": null
                }
                """.formatted(CL_ORD_ID_3, CL_ORD_ID_3))));

    FepOutboundOrderPayload payload = new FepOutboundOrderPayload(
        CL_ORD_ID_3,
        "ACC-001",
        "005930",
        FepSecurityExchange.KRX,
        FepSide.BUY,
        FepOrderType.MARKET,
        10L,
        null,
        "qsnap-1",
        java.time.Instant.parse("2026-03-01T10:00:00Z"),
        com.fix.common.fep.FepQuoteSourceMode.DELAYED,
        72000L,
        "KRW",
        "ref-client-003"
    );

    FepOrderResult result = fepClient.submitOrder(payload, "trace-client-003");

    assertThat(result.ordStatus()).isEqualTo(FepOrdStatus.PENDING);
    String body = wireMockServer.findAll(postRequestedFor(urlEqualTo("/fep/v1/orders"))).getFirst().getBodyAsString();
    assertThat(body).doesNotContain("\"price\"");
  }

  @Test
  void shouldQueryVersionedStatusContract() {
    wireMockServer.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_4)))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "ordStatus": "UNKNOWN",
                    "message": "order not found in exchange",
                    "queryTime": "2026-03-01T10:10:00Z"
                  },
                  "error": null
                }
                """.formatted(CL_ORD_ID_4))));

    FepOrderResult result = fepClient.queryOrderStatus(CL_ORD_ID_4, "trace-client-004");

    assertThat(result.clOrdId()).isEqualTo(CL_ORD_ID_4);
    assertThat(result.ordStatus()).isEqualTo(FepOrdStatus.UNKNOWN);
    assertThat(result.message()).contains("not found");

    wireMockServer.verify(getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_4)))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-client-004")));
  }

  @Test
  void shouldRejectMalformedSubmitResponsePayload() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
                    .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "WRONG-CLIENT-ID",
                    "fepOrderId": "FEP-KRX-%s",
                    "execType": "FILL",
                    "executedQty": 10,
                    "executedPrice": 72000,
                    "leavesQty": 0,
                    "transactTime": "2026-03-01T10:05:30Z"
                  },
                  "error": null
                }
                """.formatted(CL_ORD_ID_5))));

    FepOutboundOrderPayload payload = new FepOutboundOrderPayload(
        CL_ORD_ID_5,
        "ACC-001",
        "005930",
        FepSecurityExchange.KRX,
        FepSide.BUY,
        FepOrderType.LIMIT,
        10L,
        72000L,
        null,
        null,
        null,
        null,
        "KRW",
        "ref-client-005"
    );

    assertThatThrownBy(() -> fepClient.submitOrder(payload, "trace-client-005"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("clOrdId");
  }
}
