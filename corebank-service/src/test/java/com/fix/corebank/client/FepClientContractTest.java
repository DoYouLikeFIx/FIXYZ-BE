package com.fix.corebank.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import com.fix.common.web.CommonHeaders;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Instant;
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
  private static final String CL_ORD_ID_6 = "123e4567-e89b-42d3-a456-426614174206";
  private static final String CL_ORD_ID_7 = "123e4567-e89b-42d3-a456-426614174207";
  private static final String CL_ORD_ID_8 = "123e4567-e89b-42d3-a456-426614174208";
  private static final String CL_ORD_ID_9 = "123e4567-e89b-42d3-a456-426614174209";
  private static final String CL_ORD_ID_10 = "123e4567-e89b-42d3-a456-426614174210";
  private static final String CL_ORD_ID_11 = "123e4567-e89b-42d3-a456-426614174211";
  private static final String CL_ORD_ID_12 = "123e4567-e89b-42d3-a456-426614174212";

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
        .willReturn(aResponse()
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

    FepOrderResult result = fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_2, "ref-client-002"), "trace-client-002");

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
  void shouldRejectSubmitResponsesThatChangeCanonicalClOrdId() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
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

    assertThatThrownBy(() -> fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_10, "ref-client-002"), "trace-client-002b"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("submit response clOrdId must match request");
  }

  @Test
  void shouldOmitPriceFieldForMarketOrders() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
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
        Instant.parse("2026-03-01T10:00:00Z"),
        FepQuoteSourceMode.DELAYED,
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
        .willReturn(aResponse()
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
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "",
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
                """.formatted(CL_ORD_ID_5))));

    assertThatThrownBy(() -> fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_5, "ref-client-005"), "trace-client-005"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("clOrdId is required");
  }

  @Test
  void shouldMapDocumentedGatewaySubmitRcsToNormalizedTaxonomy() {
    assertMappedSubmitError(
        CL_ORD_ID_1,
        400,
        "9001",
        ErrorCode.CHANNEL_ROUTE_NOT_FOUND,
        "error.channel.route_not_found",
        "NO_ROUTE"
    );
    assertMappedSubmitError(
        CL_ORD_ID_2,
        503,
        "9002",
        ErrorCode.FEP_GATEWAY_UNAVAILABLE,
        "error.fep.unavailable",
        "POOL_EXHAUSTED"
    );
    assertMappedSubmitError(
        CL_ORD_ID_3,
        503,
        "9003",
        ErrorCode.FEP_GATEWAY_UNAVAILABLE,
        "error.fep.unavailable",
        "NOT_LOGGED_ON"
    );
    assertMappedSubmitError(
        CL_ORD_ID_4,
        504,
        "9004",
        ErrorCode.FEP_GATEWAY_TIMEOUT,
        "error.fep.timeout",
        "TIMEOUT"
    );
    assertMappedSubmitError(
        CL_ORD_ID_5,
        503,
        "9005",
        ErrorCode.FEP_GATEWAY_UNAVAILABLE,
        "error.fep.unavailable",
        "KEY_EXPIRED"
    );
    assertMappedSubmitError(
        CL_ORD_ID_6,
        400,
        "9097",
        ErrorCode.FEP_ORDER_REJECTED,
        "error.fep.rejected",
        "ORDER_REJECTED"
    );
    assertMappedSubmitError(
        CL_ORD_ID_7,
        503,
        "9098",
        ErrorCode.FEP_GATEWAY_UNAVAILABLE,
        "error.fep.unavailable",
        "CIRCUIT_OPEN"
    );
    assertMappedSubmitError(
        CL_ORD_ID_8,
        409,
        "9099",
        ErrorCode.CORE_CONCURRENCY_CONFLICT,
        "error.core.concurrency_conflict",
        "CONCURRENCY_FAILURE"
    );
  }

  @Test
  void shouldFallbackUnknownExternalGatewayCodes() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(502, "9555", "unclassified upstream failure")));

    assertThatThrownBy(() -> fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_9, "ref-unknown-001"), "trace-unknown-001"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FEP_UNKNOWN_EXTERNAL);
          assertThat(ex.getMetadata().userMessageKey()).isEqualTo("error.fep.unknown_external");
          assertThat(ex.getMetadata().operatorCode()).isEqualTo("UNKNOWN_EXTERNAL_9555");
        });
  }

  @Test
  void shouldPreserveNormalizedGatewayErrorsWhenRcIsAbsent() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withStatus(422)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "VALIDATION-001",
                  "message": "symbol is invalid"
                }
                """)));

    assertThatThrownBy(() -> fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_9, "ref-validation-001"), "trace-validation-001"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);
          assertThat(ex.getMessage()).isEqualTo("symbol is invalid");
          assertThat(ex.getMetadata()).isNull();
        });
  }

  @Test
  void shouldPreferNormalizedGatewayErrorsWhenRawRcIsUnmapped() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": false,
                  "rc": "9999",
                  "data": null,
                  "error": {
                    "code": "SYS_500",
                    "message": "gateway failed internally"
                  },
                  "traceId": "trace-9999"
                }
                """)));

    assertThatThrownBy(() -> fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_11, "ref-sys-500"), "trace-sys-500"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SYS_INTERNAL_ERROR);
          assertThat(ex.getMessage()).isEqualTo("gateway failed internally");
        });
  }

  @Test
  void shouldPreserveNormalizedAuthFailuresWhenGatewayAlsoReturnsLegacyRc() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withStatus(401)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": false,
                  "rc": "9401",
                  "data": null,
                  "error": {
                    "code": "AUTH-003",
                    "message": "Missing or invalid X-Internal-Secret"
                  },
                  "traceId": "trace-9401"
                }
                """)));

    assertThatThrownBy(() -> fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_12, "ref-auth-003"), "trace-auth-003"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED);
          assertThat(ex.getMessage()).isEqualTo(ErrorCode.AUTH_REQUIRED.defaultMessage());
        });
  }

  @Test
  void shouldTranslateStatusQueryFailuresThroughTaxonomy() {
    assertMappedStatusError(
        CL_ORD_ID_4,
        504,
        "9004",
        ErrorCode.FEP_GATEWAY_TIMEOUT,
        "error.fep.timeout",
        "TIMEOUT"
    );
    assertMappedStatusError(
        CL_ORD_ID_4,
        409,
        "9099",
        ErrorCode.CORE_CONCURRENCY_CONFLICT,
        "error.core.concurrency_conflict",
        "CONCURRENCY_FAILURE"
    );
  }

  @Test
  void shouldSupportLegacyTopLevelExternalCodeBodies() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withStatus(504)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "9004",
                  "message": "legacy timeout body"
                }
                """)));

    assertThatThrownBy(() -> fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_9, "ref-legacy-001"), "trace-legacy-001"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FEP_GATEWAY_TIMEOUT);
          assertThat(ex.getMetadata().userMessageKey()).isEqualTo("error.fep.timeout");
          assertThat(ex.getMetadata().operatorCode()).isEqualTo("TIMEOUT");
        });
  }

  private void assertMappedSubmitError(
      String clOrdId,
      int httpStatus,
      String externalRc,
      ErrorCode expectedErrorCode,
      String expectedUserMessageKey,
      String expectedOperatorCode
  ) {
    wireMockServer.resetAll();
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(httpStatus, externalRc, "failure " + externalRc)));

    assertThatThrownBy(() -> fepClient.submitOrder(buildSubmitPayload(clOrdId, "ref-" + externalRc), "trace-" + externalRc))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(expectedErrorCode);
          assertThat(ex.getMetadata().userMessageKey()).isEqualTo(expectedUserMessageKey);
          assertThat(ex.getMetadata().operatorCode()).isEqualTo(expectedOperatorCode);
        });
  }

  private void assertMappedStatusError(
      String clOrdId,
      int httpStatus,
      String externalRc,
      ErrorCode expectedErrorCode,
      String expectedUserMessageKey,
      String expectedOperatorCode
  ) {
    wireMockServer.resetAll();
    wireMockServer.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(clOrdId)))
        .willReturn(canonicalGatewayError(httpStatus, externalRc, "status failure " + externalRc)));

    assertThatThrownBy(() -> fepClient.queryOrderStatus(clOrdId, "trace-status-" + externalRc))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(expectedErrorCode);
          assertThat(ex.getMetadata().userMessageKey()).isEqualTo(expectedUserMessageKey);
          assertThat(ex.getMetadata().operatorCode()).isEqualTo(expectedOperatorCode);
        });
  }

  private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder canonicalGatewayError(
      int httpStatus,
      String externalRc,
      String message
  ) {
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
            """.formatted(
            externalRc,
            FepExternalErrorTaxonomy.resolve(externalRc).errorCode().code(),
            message,
            FepExternalErrorTaxonomy.resolve(externalRc).metadata().operatorCode(),
            externalRc
        ));
  }

  private FepOutboundOrderPayload buildSubmitPayload(String clOrdId, String referenceId) {
    return new FepOutboundOrderPayload(
        clOrdId,
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
        referenceId
    );
  }
}
