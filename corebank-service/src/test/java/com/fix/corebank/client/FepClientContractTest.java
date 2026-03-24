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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import com.fix.common.validation.ContractPatterns;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class FepClientContractTest {

  private static final Set<String> BASELINE_SUBMIT_FIELDS = Set.of(
      "clOrdId",
      "accountId",
      "symbol",
      "securityExchange",
      "side",
      "orderType",
      "qty",
      "currency",
      "referenceId"
  );

  private static final Set<String> SUBMIT_SUCCESS_RESPONSE_FIELDS = Set.of(
      "clOrdId",
      "fepOrderId",
      "execType",
      "ordStatus",
      "executedQty",
      "executedPrice",
      "leavesQty",
      "transactTime"
  );

  private static final Set<String> STATUS_HEADER_PARAMETERS = Set.of(
      CommonHeaders.X_INTERNAL_SECRET,
      CommonHeaders.X_CORRELATION_ID
  );

  private static final Set<String> SUBMIT_HEADER_PARAMETERS = Set.of(
      CommonHeaders.X_INTERNAL_SECRET,
      CommonHeaders.X_CORRELATION_ID,
      CommonHeaders.X_CL_ORD_ID
  );

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
  private static final String CL_ORD_ID_13 = "123e4567-e89b-42d3-a456-426614174213";
  private static final String CL_ORD_ID_14 = "123e4567-e89b-42d3-a456-426614174214";
  private static final String CL_ORD_ID_15 = "123e4567-e89b-42d3-a456-426614174215";
  private static final String CL_ORD_ID_16 = "123e4567-e89b-42d3-a456-426614174216";
  private static final String CL_ORD_ID_17 = "123e4567-e89b-42d3-a456-426614174217";
  private static final String CL_ORD_ID_18 = "123e4567-e89b-42d3-a456-426614174218";
  private static final String SUBMIT_SUCCESS_RESPONSE_FIXTURE = "contracts/fep/submit-success-fill-response.json";
  private static final String SUBMIT_REQUEST_FIXTURE = "contracts/fep/submit-limit-request.json";
  private static final String SUBMIT_MARKET_REQUEST_FIXTURE = "contracts/fep/submit-market-request.json";
  private static final String SUBMIT_DRIFTED_RESPONSE_FIXTURE = "contracts/fep/submit-success-drifted-clordid-response.json";
  private static final String ERROR_RESPONSE_FIXTURE = "contracts/fep/gateway-error-response.json";
  private static final String STATUS_UNKNOWN_RESPONSE_FIXTURE = "contracts/fep/status-unknown-response.json";
  private static final String STATUS_PARTIALLY_FILLED_RESPONSE_FIXTURE =
      "contracts/fep/status-partially-filled-response.json";
  private static final String STATUS_PENDING_RESPONSE_FIXTURE = "contracts/fep/status-pending-response.json";
  private static final String STATUS_REJECTED_RESPONSE_FIXTURE = "contracts/fep/status-rejected-response.json";
  private static final String STATUS_CANCELED_RESPONSE_FIXTURE = "contracts/fep/status-canceled-response.json";
  private static final String STATUS_CANCELED_PARTIAL_RESPONSE_FIXTURE =
      "contracts/fep/status-canceled-partial-response.json";
  private static final String STATUS_MALFORMED_RESPONSE_FIXTURE = "contracts/fep/status-malformed-response.json";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            .withBody(contractFixture(
                SUBMIT_SUCCESS_RESPONSE_FIXTURE,
                Map.of(
                    "clOrdId", CL_ORD_ID_2,
                    "fepOrderId", "FEP-KRX-" + CL_ORD_ID_2,
                    "transactTime", "2026-03-01T10:05:30Z"
                )
            ))));

    FepOrderResult result = fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_2, "ref-client-002"), "trace-client-002");

    assertThat(result.clOrdId()).isEqualTo(CL_ORD_ID_2);
    assertThat(result.fepOrderId()).isEqualTo("FEP-KRX-" + CL_ORD_ID_2);
    assertThat(result.execType()).isEqualTo(FepExecType.FILL);
    assertThat(result.ordStatus()).isEqualTo(FepOrdStatus.FILLED);
    assertThat(result.executedQty()).isEqualTo(10L);
    assertThat(result.executedPrice()).isEqualTo(72000L);
    assertThat(result.leavesQty()).isZero();
    assertThat(result.transactTime()).isEqualTo(Instant.parse("2026-03-01T10:05:30Z"));

    wireMockServer.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-client-002"))
        .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(CL_ORD_ID_2))
        .withRequestBody(equalToJson(
            contractFixture(
                SUBMIT_REQUEST_FIXTURE,
                Map.of(
                    "clOrdId", CL_ORD_ID_2,
                    "referenceId", "ref-client-002"
                )
            ),
            true,
            false
        )));
  }

  @Test
  void shouldForwardTraceparentHeaderToFepGateway() {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    TraceparentSupport.putInMdc(traceparent);
    try {
      wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
          .willReturn(aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(contractFixture(
                  SUBMIT_SUCCESS_RESPONSE_FIXTURE,
                  Map.of(
                      "clOrdId", CL_ORD_ID_2,
                      "fepOrderId", "FEP-KRX-" + CL_ORD_ID_2,
                      "transactTime", "2026-03-01T10:05:30Z"
                  )
              ))));

      fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_2, "ref-client-trace"), "trace-client-traceparent");

      wireMockServer.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
          .withHeader(CommonHeaders.TRACEPARENT, equalTo(traceparent)));
    } finally {
      CorrelationIdSupport.clearMdc();
    }
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
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getMessage()).contains("submit response clOrdId must match request");
          assertThat(ex.getMetadata()).isNotNull();
          assertThat(ex.getMetadata().operatorCode()).isEqualTo("DOWNSTREAM_CL_ORD_ID_MISMATCH");
        });
  }

  @Test
  void shouldFailWithExplicitMismatchWhenCanonicalSubmitFixtureDriftsFieldName() {
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(contractFixture(
                SUBMIT_DRIFTED_RESPONSE_FIXTURE,
                Map.of(
                    "clOrdId", CL_ORD_ID_2,
                    "fepOrderId", "FEP-KRX-" + CL_ORD_ID_2,
                    "transactTime", "2026-03-01T10:05:30Z"
                )
            ))));

    assertThatThrownBy(() -> fepClient.submitOrder(buildSubmitPayload(CL_ORD_ID_2, "ref-client-002"), "trace-client-drift"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);
          assertThat(ex.getMessage()).isEqualTo("clOrdId is required in submit response");
        });
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

    assertThat(result.clOrdId()).isEqualTo(CL_ORD_ID_3);
    assertThat(result.fepOrderId()).isEqualTo("FEP-KRX-" + CL_ORD_ID_3);
    assertThat(result.execType()).isEqualTo(FepExecType.PENDING_NEW);
    assertThat(result.ordStatus()).isEqualTo(FepOrdStatus.PENDING);
    assertThat(result.executedQty()).isZero();
    assertThat(result.leavesQty()).isEqualTo(10L);

    wireMockServer.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-client-003"))
        .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(CL_ORD_ID_3))
        .withRequestBody(equalToJson(
            contractFixture(
                SUBMIT_MARKET_REQUEST_FIXTURE,
                Map.of(
                    "clOrdId", CL_ORD_ID_3,
                    "quoteSnapshotId", "qsnap-1",
                    "quoteAsOf", "2026-03-01T10:00:00Z",
                    "preTradePrice", "72000",
                    "referenceId", "ref-client-003"
                )
            ),
            true,
            false
        )));
  }

  @ParameterizedTest(name = "status success {0}")
  @MethodSource("documentedStatusSuccessContracts")
  void shouldQueryVersionedStatusContractAcrossDocumentedSuccessShapes(
      String scenario,
      String clOrdId,
      String fixturePath,
      Map<String, String> replacements,
      String expectedFepOrderId,
      FepExecType expectedExecType,
      FepOrdStatus expectedOrdStatus,
      Long expectedExecutedQty,
      Long expectedExecutedPrice,
      Long expectedLeavesQty,
      Instant expectedTransactTime,
      Instant expectedQueryTime,
      String expectedMessage,
      String expectedRejectReason,
      Long expectedCanceledQty,
      String expectedParseError
  ) {
    wireMockServer.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(clOrdId)))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(contractFixture(fixturePath, replacements))));

    String correlationId = "trace-status-success-" + scenario;
    FepOrderResult result = fepClient.queryOrderStatus(clOrdId, correlationId);

    assertThat(result.clOrdId()).isEqualTo(clOrdId);
    assertThat(result.fepOrderId()).isEqualTo(expectedFepOrderId);
    assertThat(result.execType()).isEqualTo(expectedExecType);
    assertThat(result.ordStatus()).isEqualTo(expectedOrdStatus);
    assertThat(result.executedQty()).isEqualTo(expectedExecutedQty);
    assertThat(result.executedPrice()).isEqualTo(expectedExecutedPrice);
    assertThat(result.leavesQty()).isEqualTo(expectedLeavesQty);
    assertThat(result.transactTime()).isEqualTo(expectedTransactTime);
    assertThat(result.queryTime()).isEqualTo(expectedQueryTime);
    assertThat(result.message()).isEqualTo(expectedMessage);
    assertThat(result.rejectReason()).isEqualTo(expectedRejectReason);
    assertThat(result.canceledQty()).isEqualTo(expectedCanceledQty);
    assertThat(result.parseError()).isEqualTo(expectedParseError);

    wireMockServer.verify(getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(clOrdId)))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo(correlationId)));
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
  void shouldRejectRejectedStatusResponseWithoutRejectReason() {
    wireMockServer.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_15)))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "ordStatus": "REJECTED",
                    "execType": "REJECTED",
                    "transactTime": "2026-03-01T10:06:00Z",
                    "queryTime": "2026-03-01T10:10:00Z"
                  }
                }
                """.formatted(CL_ORD_ID_15))));

    assertThatThrownBy(() -> fepClient.queryOrderStatus(CL_ORD_ID_15, "trace-status-missing-reject-reason"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);
          assertThat(ex.getMessage()).isEqualTo("rejectReason is required when ordStatus is REJECTED");
        });
  }

  @Test
  void shouldRejectMalformedStatusResponseWithoutParseError() {
    wireMockServer.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_17)))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "ordStatus": "MALFORMED",
                    "message": "FIX ExecutionReport parse failed; manual review required",
                    "queryTime": "2026-03-01T10:10:00Z"
                  }
                }
                """.formatted(CL_ORD_ID_17))));

    assertThatThrownBy(() -> fepClient.queryOrderStatus(CL_ORD_ID_17, "trace-status-missing-parse-error"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);
          assertThat(ex.getMessage()).isEqualTo("parseError is required when ordStatus is MALFORMED");
        });
  }

  @Test
  void shouldRejectPendingStatusResponseWithoutMessage() {
    wireMockServer.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_14)))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "%s",
                    "ordStatus": "PENDING",
                    "queryTime": "2026-03-01T10:10:00Z"
                  }
                }
                """.formatted(CL_ORD_ID_14))));

    assertThatThrownBy(() -> fepClient.queryOrderStatus(CL_ORD_ID_14, "trace-status-missing-pending-message"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONTRACT_VALIDATION_FAILED);
          assertThat(ex.getMessage()).isEqualTo("message is required when ordStatus is PENDING");
        });
  }

  @ParameterizedTest(name = "submit rc {1} -> {2}")
  @MethodSource("documentedSubmitErrorMappings")
  void shouldMapDocumentedGatewaySubmitRcsToNormalizedTaxonomy(
      String clOrdId,
      int httpStatus,
      String externalRc,
      ErrorCode expectedErrorCode,
      String expectedUserMessageKey,
      String expectedOperatorCode
  ) {
    assertMappedSubmitError(
        clOrdId,
        httpStatus,
        externalRc,
        expectedErrorCode,
        expectedUserMessageKey,
        expectedOperatorCode
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

  @ParameterizedTest(name = "status rc {1} -> {2}")
  @MethodSource("documentedStatusErrorMappings")
  void shouldTranslateStatusQueryFailuresThroughTaxonomy(
      String clOrdId,
      int httpStatus,
      String externalRc,
      ErrorCode expectedErrorCode,
      String expectedUserMessageKey,
      String expectedOperatorCode
  ) {
    assertMappedStatusError(
        clOrdId,
        httpStatus,
        externalRc,
        expectedErrorCode,
        expectedUserMessageKey,
        expectedOperatorCode
    );
  }

  @Test
  void shouldPreserveNormalizedStatusAuthFailuresWhenGatewayAlsoReturnsLegacyRc() {
    wireMockServer.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_12)))
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
                  "traceId": "trace-status-9401"
                }
                """)));

    assertThatThrownBy(() -> fepClient.queryOrderStatus(CL_ORD_ID_12, "trace-status-auth-003"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_REQUIRED);
          assertThat(ex.getMessage()).isEqualTo("Missing or invalid X-Internal-Secret");
        });

    wireMockServer.verify(getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_12)))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-status-auth-003")));
  }

  @Test
  void shouldPreserveNormalizedStatusSystemErrorsWhenRawRcIsUnmapped() {
    wireMockServer.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_11)))
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
                  "traceId": "trace-status-9999"
                }
                """)));

    assertThatThrownBy(() -> fepClient.queryOrderStatus(CL_ORD_ID_11, "trace-status-sys-500"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SYS_INTERNAL_ERROR);
          assertThat(ex.getMessage()).isEqualTo("gateway failed internally");
        });

    wireMockServer.verify(getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(CL_ORD_ID_11)))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo("trace-status-sys-500")));
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

  @Test
  void shouldKeepCanonicalFixturesAlignedWithOpenApiSnapshot() throws Exception {
    JsonNode contract = OBJECT_MAPPER.readTree(Files.readString(openApiContract()));
    JsonNode submitOperation = contract.path("paths").path("/fep/v1/orders").path("post");
    JsonNode statusOperation = contract.path("paths").path("/fep/v1/orders/{clOrdId}/status").path("get");
    JsonNode submitSchema = contract.path("components").path("schemas").path("FepOrderSubmitRequest");
    JsonNode responseSchema = contract.path("components").path("schemas").path("FepOrderResponse");
    JsonNode responseEnvelopeSchema = contract.path("components").path("schemas").path("ApiResponseFepOrderResponse");
    JsonNode errorSchema = contract.path("components").path("schemas").path("ApiErrorResponse");

    assertThat(headerParameterNames(submitOperation.path("parameters")))
        .containsExactlyInAnyOrderElementsOf(SUBMIT_HEADER_PARAMETERS);
    assertThat(headerParameterNames(statusOperation.path("parameters")))
        .containsExactlyInAnyOrderElementsOf(STATUS_HEADER_PARAMETERS);
    assertThat(pathParameterNames(statusOperation.path("parameters")))
        .containsExactly("clOrdId");
    assertParameterContract(submitOperation.path("parameters"), "header", CommonHeaders.X_INTERNAL_SECRET, true, "string", null);
    assertParameterContract(submitOperation.path("parameters"), "header", CommonHeaders.X_CORRELATION_ID, true, "string", null);
    assertParameterContract(submitOperation.path("parameters"), "header", CommonHeaders.X_CL_ORD_ID, true, "string", ContractPatterns.UUID_V4);
    assertParameterContract(statusOperation.path("parameters"), "header", CommonHeaders.X_INTERNAL_SECRET, true, "string", null);
    assertParameterContract(statusOperation.path("parameters"), "header", CommonHeaders.X_CORRELATION_ID, true, "string", null);
    assertParameterContract(statusOperation.path("parameters"), "path", "clOrdId", true, "string", ContractPatterns.UUID_V4);

    assertRequestFixtureMatchesOpenApi(
        SUBMIT_REQUEST_FIXTURE,
        Map.of("clOrdId", CL_ORD_ID_2, "referenceId", "ref-client-002"),
        union(BASELINE_SUBMIT_FIELDS, Set.of("price")),
        submitSchema
    );
    assertRequestFixtureMatchesOpenApi(
        SUBMIT_MARKET_REQUEST_FIXTURE,
        Map.of(
            "clOrdId", CL_ORD_ID_3,
            "quoteSnapshotId", "qsnap-1",
            "quoteAsOf", "2026-03-01T10:00:00Z",
            "preTradePrice", "72000",
            "referenceId", "ref-client-003"
        ),
        union(BASELINE_SUBMIT_FIELDS, Set.of("quoteSnapshotId", "quoteAsOf", "quoteSourceMode", "preTradePrice")),
        submitSchema
    );
    assertResponseFixtureMatchesOpenApi(
        SUBMIT_SUCCESS_RESPONSE_FIXTURE,
        Map.of(
            "clOrdId", CL_ORD_ID_2,
            "fepOrderId", "FEP-KRX-" + CL_ORD_ID_2,
            "transactTime", "2026-03-01T10:05:30Z"
        ),
        SUBMIT_SUCCESS_RESPONSE_FIELDS,
        responseEnvelopeSchema,
        responseSchema
    );
    assertResponseFixtureMatchesOpenApi(
        STATUS_UNKNOWN_RESPONSE_FIXTURE,
        Map.of(
            "clOrdId", CL_ORD_ID_4,
            "queryTime", "2026-03-01T10:10:00Z",
            "message", "order not found in exchange"
        ),
        Set.of("clOrdId", "ordStatus", "message", "queryTime"),
        responseEnvelopeSchema,
        responseSchema
    );
    assertResponseFixtureMatchesOpenApi(
        STATUS_PARTIALLY_FILLED_RESPONSE_FIXTURE,
        Map.of(
            "clOrdId", CL_ORD_ID_13,
            "fepOrderId", "FEP-KRX-" + CL_ORD_ID_13,
            "transactTime", "2026-03-01T10:05:30Z",
            "queryTime", "2026-03-01T10:10:00Z"
        ),
        Set.of(
            "clOrdId",
            "fepOrderId",
            "ordStatus",
            "execType",
            "executedQty",
            "executedPrice",
            "leavesQty",
            "transactTime",
            "queryTime"
        ),
        responseEnvelopeSchema,
        responseSchema
    );
    assertResponseFixtureMatchesOpenApi(
        STATUS_PENDING_RESPONSE_FIXTURE,
        Map.of(
            "clOrdId", CL_ORD_ID_14,
            "queryTime", "2026-03-01T10:10:00Z",
            "message", "execution report is still pending"
        ),
        Set.of("clOrdId", "ordStatus", "queryTime", "message"),
        responseEnvelopeSchema,
        responseSchema
    );
    assertResponseFixtureMatchesOpenApi(
        STATUS_REJECTED_RESPONSE_FIXTURE,
        Map.of(
            "clOrdId", CL_ORD_ID_15,
            "transactTime", "2026-03-01T10:06:00Z",
            "queryTime", "2026-03-01T10:10:00Z",
            "rejectReason", "INSUFFICIENT_FUNDS"
        ),
        Set.of("clOrdId", "ordStatus", "execType", "rejectReason", "transactTime", "queryTime"),
        responseEnvelopeSchema,
        responseSchema
    );
    assertResponseFixtureMatchesOpenApi(
        STATUS_CANCELED_RESPONSE_FIXTURE,
        Map.of(
            "clOrdId", CL_ORD_ID_16,
            "transactTime", "2026-03-01T10:06:00Z",
            "queryTime", "2026-03-01T10:10:00Z"
        ),
        Set.of(
            "clOrdId",
            "ordStatus",
            "execType",
            "canceledQty",
            "transactTime",
            "queryTime"
        ),
        responseEnvelopeSchema,
        responseSchema
    );
    assertResponseFixtureMatchesOpenApi(
        STATUS_CANCELED_PARTIAL_RESPONSE_FIXTURE,
        Map.of(
            "clOrdId", CL_ORD_ID_18,
            "fepOrderId", "FEP-KRX-" + CL_ORD_ID_18,
            "transactTime", "2026-03-01T10:06:00Z",
            "queryTime", "2026-03-01T10:10:00Z"
        ),
        Set.of(
            "clOrdId",
            "fepOrderId",
            "ordStatus",
            "execType",
            "executedQty",
            "executedPrice",
            "canceledQty",
            "transactTime",
            "queryTime"
        ),
        responseEnvelopeSchema,
        responseSchema
    );
    assertResponseFixtureMatchesOpenApi(
        STATUS_MALFORMED_RESPONSE_FIXTURE,
        Map.of(
            "clOrdId", CL_ORD_ID_17,
            "queryTime", "2026-03-01T10:10:00Z",
            "message", "FIX ExecutionReport parse failed; manual review required",
            "parseError", "PARSE_ERROR:Tag 39 missing or invalid"
        ),
        Set.of("clOrdId", "ordStatus", "queryTime", "message", "parseError"),
        responseEnvelopeSchema,
        responseSchema
    );
    assertErrorFixtureMatchesOpenApi(
        ERROR_RESPONSE_FIXTURE,
        Map.of(
            "code", "9004",
            "message", "failure 9004",
            "userMessageKey", "error.fep.timeout",
            "operatorCode", "TIMEOUT",
            "timestamp", "2026-03-01T10:10:00Z"
        ),
        Set.of("code", "message", "userMessageKey", "operatorCode", "timestamp"),
        errorSchema
    );
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
    String correlationId = "trace-" + externalRc;
    wireMockServer.stubFor(post(urlEqualTo("/fep/v1/orders"))
        .willReturn(canonicalGatewayError(httpStatus, externalRc, "failure " + externalRc)));

    assertThatThrownBy(() -> fepClient.submitOrder(buildSubmitPayload(clOrdId, "ref-" + externalRc), correlationId))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(expectedErrorCode);
          assertThat(ex.getMetadata().userMessageKey()).isEqualTo(expectedUserMessageKey);
          assertThat(ex.getMetadata().operatorCode()).isEqualTo(expectedOperatorCode);
        });

    wireMockServer.verify(postRequestedFor(urlEqualTo("/fep/v1/orders"))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo(correlationId))
        .withHeader(CommonHeaders.X_CL_ORD_ID, equalTo(clOrdId)));
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
    String correlationId = "trace-status-" + externalRc;
    wireMockServer.stubFor(get(urlEqualTo("/fep/v1/orders/%s/status".formatted(clOrdId)))
        .willReturn(canonicalGatewayError(httpStatus, externalRc, "status failure " + externalRc)));

    assertThatThrownBy(() -> fepClient.queryOrderStatus(clOrdId, correlationId))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(expectedErrorCode);
          assertThat(ex.getMetadata().userMessageKey()).isEqualTo(expectedUserMessageKey);
          assertThat(ex.getMetadata().operatorCode()).isEqualTo(expectedOperatorCode);
        });

    wireMockServer.verify(getRequestedFor(urlEqualTo("/fep/v1/orders/%s/status".formatted(clOrdId)))
        .withHeader(CommonHeaders.X_INTERNAL_SECRET, equalTo("test-internal-secret"))
        .withHeader(CommonHeaders.X_CORRELATION_ID, equalTo(correlationId)));
  }

  private com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder canonicalGatewayError(
      int httpStatus,
      String externalRc,
      String message
  ) {
    FepExternalErrorTaxonomy.TaxonomyEntry taxonomyEntry = FepExternalErrorTaxonomy.resolve(externalRc);
    return aResponse()
        .withStatus(httpStatus)
        .withHeader("Content-Type", "application/json")
        .withBody(contractFixture(
            ERROR_RESPONSE_FIXTURE,
            Map.of(
                "code", externalRc,
                "message", message,
                "userMessageKey", taxonomyEntry.metadata().userMessageKey(),
                "operatorCode", taxonomyEntry.metadata().operatorCode(),
                "timestamp", "2026-03-01T10:10:00Z"
            )
        ));
  }

  private void assertRequestFixtureMatchesOpenApi(
      String resourcePath,
      Map<String, String> replacements,
      Set<String> expectedFields,
      JsonNode submitSchema
  ) throws Exception {
    JsonNode request = parseContractFixture(resourcePath, replacements);
    Set<String> requestFields = fieldNames(request);
    Set<String> submitSchemaFields = fieldNames(submitSchema.path("properties"));

    assertThat(requestFields).containsExactlyInAnyOrderElementsOf(expectedFields);
    assertThat(submitSchemaFields).containsAll(requestFields);
    assertRequiredFieldsPresent(request, submitSchema);
    assertJsonObjectMatchesSchema(request, submitSchema.path("properties"));
  }

  private void assertResponseFixtureMatchesOpenApi(
      String resourcePath,
      Map<String, String> replacements,
      Set<String> expectedDataFields,
      JsonNode responseEnvelopeSchema,
      JsonNode responseSchema
  ) throws Exception {
    JsonNode response = parseContractFixture(resourcePath, replacements);
    Set<String> responseEnvelopeFields = fieldNames(response);
    Set<String> responseEnvelopeSchemaFields = fieldNames(responseEnvelopeSchema.path("properties"));
    Set<String> responseSchemaFields = fieldNames(responseSchema.path("properties"));

    assertThat(responseEnvelopeFields).contains("success", "data");
    assertThat(responseEnvelopeSchemaFields).containsAll(responseEnvelopeFields);
    assertRequiredFieldsPresent(response, responseEnvelopeSchema);
    assertThat(response.path("success").asBoolean()).isTrue();
    assertJsonValueMatchesSchema("success", response.path("success"), responseEnvelopeSchema.path("properties").path("success"));
    if (response.has("error")) {
      assertJsonValueMatchesSchema("error", response.path("error"), responseEnvelopeSchema.path("properties").path("error"));
    }
    if (response.has("timestamp")) {
      assertJsonValueMatchesSchema("timestamp", response.path("timestamp"), responseEnvelopeSchema.path("properties").path("timestamp"));
    }

    Set<String> responseFields = fieldNames(response.path("data"));
    assertThat(responseFields).containsExactlyInAnyOrderElementsOf(expectedDataFields);
    assertThat(responseSchemaFields).containsAll(responseFields);
    assertRequiredFieldsPresent(response.path("data"), responseSchema);
    assertJsonObjectMatchesSchema(response.path("data"), responseSchema.path("properties"));
  }

  private void assertErrorFixtureMatchesOpenApi(
      String resourcePath,
      Map<String, String> replacements,
      Set<String> expectedFields,
      JsonNode errorSchema
  ) throws Exception {
    JsonNode error = parseContractFixture(resourcePath, replacements);
    Set<String> errorFields = fieldNames(error);
    Set<String> errorSchemaFields = fieldNames(errorSchema.path("properties"));

    assertThat(errorFields).containsExactlyInAnyOrderElementsOf(expectedFields);
    assertThat(errorSchemaFields).containsAll(errorFields);
    assertRequiredFieldsPresent(error, errorSchema);
    assertJsonObjectMatchesSchema(error, errorSchema.path("properties"));
  }

  private void assertRequiredFieldsPresent(JsonNode value, JsonNode schema) {
    Set<String> requiredFields = requiredFields(schema);
    if (!requiredFields.isEmpty()) {
      assertThat(fieldNames(value)).containsAll(requiredFields);
    }
  }

  private void assertJsonObjectMatchesSchema(JsonNode value, JsonNode schemaProperties) {
    value.fields().forEachRemaining(field -> assertJsonValueMatchesSchema(field.getKey(), field.getValue(), schemaProperties.path(field.getKey())));
  }

  private void assertJsonValueMatchesSchema(String fieldName, JsonNode value, JsonNode schema) {
    assertThat(schema.isMissingNode()).as("OpenAPI schema is missing field %s", fieldName).isFalse();
    if (value.isNull()) {
      assertThat(schemaAllowsNull(schema)).as("%s must not be null unless the schema allows it", fieldName).isTrue();
      return;
    }

    String type = schema.path("type").asText();
    if (!type.isBlank()) {
      switch (type) {
        case "boolean" -> assertThat(value.isBoolean()).as("%s must be a boolean", fieldName).isTrue();
        case "integer" -> assertThat(value.isIntegralNumber()).as("%s must be an integer", fieldName).isTrue();
        case "string" -> assertThat(value.isTextual()).as("%s must be a string", fieldName).isTrue();
        case "object" -> assertThat(value.isObject()).as("%s must be an object", fieldName).isTrue();
        default -> {
        }
      }
    }

    if (value.isTextual()) {
      String text = value.asText();
      if (schema.has("minLength")) {
        assertThat(text.length()).as("%s must satisfy minLength", fieldName).isGreaterThanOrEqualTo(schema.path("minLength").asInt());
      }
      if (schema.has("maxLength")) {
        assertThat(text.length()).as("%s must satisfy maxLength", fieldName).isLessThanOrEqualTo(schema.path("maxLength").asInt());
      }
      if (schema.has("pattern")) {
        assertThat(text).as("%s must satisfy pattern", fieldName).matches(schema.path("pattern").asText());
      }
      if ("date-time".equals(schema.path("format").asText())) {
        assertThatCode(() -> Instant.parse(text)).as("%s must be ISO date-time", fieldName).doesNotThrowAnyException();
      }
      if (schema.has("enum")) {
        Set<String> allowedValues = new TreeSet<>();
        schema.path("enum").forEach(enumValue -> allowedValues.add(enumValue.asText()));
        assertThat(allowedValues).as("%s must be an allowed enum value", fieldName).contains(text);
      }
    }
  }

  private boolean schemaAllowsNull(JsonNode schema) {
    if (schema.path("nullable").asBoolean(false)) {
      return true;
    }
    JsonNode type = schema.path("type");
    if (type.isArray()) {
      for (JsonNode typeValue : type) {
        if ("null".equals(typeValue.asText())) {
          return true;
        }
      }
    }
    return "null".equals(type.asText());
  }

  private void assertParameterContract(
      JsonNode parameters,
      String location,
      String name,
      boolean expectedRequired,
      String expectedType,
      String expectedPattern
  ) {
    JsonNode parameter = parameter(parameters, location, name);
    assertThat(parameter.isMissingNode()).as("Missing %s parameter %s", location, name).isFalse();
    assertThat(parameter.path("required").asBoolean()).as("%s should be required", name).isEqualTo(expectedRequired);
    assertThat(parameter.path("schema").path("type").asText()).as("%s should be %s", name, expectedType).isEqualTo(expectedType);
    if (expectedPattern != null) {
      assertThat(parameter.path("schema").path("pattern").asText()).as("%s pattern drifted", name).isEqualTo(expectedPattern);
    }
  }

  private JsonNode parameter(JsonNode parameters, String location, String name) {
    for (JsonNode parameter : parameters) {
      if (location.equals(parameter.path("in").asText()) && name.equals(parameter.path("name").asText())) {
        return parameter;
      }
    }
    return MissingNode.getInstance();
  }

  private Set<String> headerParameterNames(JsonNode parameters) {
    Set<String> names = new TreeSet<>();
    for (JsonNode parameter : parameters) {
      if ("header".equals(parameter.path("in").asText())) {
        names.add(parameter.path("name").asText());
      }
    }
    return names;
  }

  private Set<String> pathParameterNames(JsonNode parameters) {
    Set<String> names = new TreeSet<>();
    for (JsonNode parameter : parameters) {
      if ("path".equals(parameter.path("in").asText())) {
        names.add(parameter.path("name").asText());
      }
    }
    return names;
  }

  private Set<String> requiredFields(JsonNode schema) {
    Set<String> names = new TreeSet<>();
    schema.path("required").forEach(field -> names.add(field.asText()));
    return names;
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

  private String contractFixture(String resourcePath, Map<String, String> replacements) {
    String body = loadResource(resourcePath);
    for (Map.Entry<String, String> replacement : replacements.entrySet()) {
      body = body.replace("{{" + replacement.getKey() + "}}", replacement.getValue());
    }
    assertThat(body).doesNotContain("{{");
    return body;
  }

  private JsonNode parseContractFixture(String resourcePath, Map<String, String> replacements) throws Exception {
    return OBJECT_MAPPER.readTree(contractFixture(resourcePath, replacements));
  }

  private String loadResource(String resourcePath) {
    try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException("Unable to load contract fixture: " + resourcePath, ex);
    }
  }

  private Path openApiContract() {
    Path current = Path.of(System.getProperty("user.dir"));
    for (int i = 0; i < 3 && current != null; i++) {
      Path candidate = current.resolve("contracts").resolve("openapi").resolve("fep-gateway.json");
      if (Files.exists(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    return Path.of(System.getProperty("user.dir"), "contracts", "openapi", "fep-gateway.json");
  }

  private Set<String> fieldNames(JsonNode node) {
    Set<String> names = new TreeSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private Set<String> union(Set<String> left, Set<String> right) {
    Set<String> fields = new TreeSet<>(left);
    fields.addAll(right);
    return fields;
  }

  private static Stream<Arguments> documentedSubmitErrorMappings() {
    return Stream.of(
        Arguments.of(CL_ORD_ID_1, 400, "9001", ErrorCode.CHANNEL_ROUTE_NOT_FOUND, "error.channel.route_not_found", "NO_ROUTE"),
        Arguments.of(CL_ORD_ID_2, 503, "9002", ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "POOL_EXHAUSTED"),
        Arguments.of(CL_ORD_ID_3, 503, "9003", ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "NOT_LOGGED_ON"),
        Arguments.of(CL_ORD_ID_4, 504, "9004", ErrorCode.FEP_GATEWAY_TIMEOUT, "error.fep.timeout", "TIMEOUT"),
        Arguments.of(CL_ORD_ID_5, 503, "9005", ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "KEY_EXPIRED"),
        Arguments.of(CL_ORD_ID_6, 400, "9097", ErrorCode.FEP_ORDER_REJECTED, "error.fep.rejected", "ORDER_REJECTED"),
        Arguments.of(CL_ORD_ID_7, 503, "9098", ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "CIRCUIT_OPEN"),
        Arguments.of(CL_ORD_ID_8, 409, "9099", ErrorCode.CORE_CONCURRENCY_CONFLICT, "error.core.concurrency_conflict", "CONCURRENCY_FAILURE")
    );
  }

  private static Stream<Arguments> documentedStatusErrorMappings() {
    return Stream.of(
        Arguments.of(CL_ORD_ID_2, 503, "9002", ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "POOL_EXHAUSTED"),
        Arguments.of(CL_ORD_ID_3, 503, "9003", ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "NOT_LOGGED_ON"),
        Arguments.of(CL_ORD_ID_4, 504, "9004", ErrorCode.FEP_GATEWAY_TIMEOUT, "error.fep.timeout", "TIMEOUT"),
        Arguments.of(CL_ORD_ID_7, 503, "9098", ErrorCode.FEP_GATEWAY_UNAVAILABLE, "error.fep.unavailable", "CIRCUIT_OPEN")
    );
  }

  private static Stream<Arguments> documentedStatusSuccessContracts() {
    return Stream.of(
        Arguments.of(
            "unknown",
            CL_ORD_ID_4,
            STATUS_UNKNOWN_RESPONSE_FIXTURE,
            Map.of(
                "clOrdId", CL_ORD_ID_4,
                "queryTime", "2026-03-01T10:10:00Z",
                "message", "order not found in exchange"
            ),
            null,
            null,
            FepOrdStatus.UNKNOWN,
            null,
            null,
            null,
            null,
            Instant.parse("2026-03-01T10:10:00Z"),
            "order not found in exchange",
            null,
            null,
            null
        ),
        Arguments.of(
            "partially-filled",
            CL_ORD_ID_13,
            STATUS_PARTIALLY_FILLED_RESPONSE_FIXTURE,
            Map.of(
                "clOrdId", CL_ORD_ID_13,
                "fepOrderId", "FEP-KRX-" + CL_ORD_ID_13,
                "transactTime", "2026-03-01T10:05:30Z",
                "queryTime", "2026-03-01T10:10:00Z"
            ),
            "FEP-KRX-" + CL_ORD_ID_13,
            FepExecType.PARTIAL_FILL,
            FepOrdStatus.PARTIALLY_FILLED,
            5L,
            72000L,
            5L,
            Instant.parse("2026-03-01T10:05:30Z"),
            Instant.parse("2026-03-01T10:10:00Z"),
            null,
            null,
            null,
            null
        ),
        Arguments.of(
            "pending",
            CL_ORD_ID_14,
            STATUS_PENDING_RESPONSE_FIXTURE,
            Map.of(
                "clOrdId", CL_ORD_ID_14,
                "queryTime", "2026-03-01T10:10:00Z",
                "message", "execution report is still pending"
            ),
            null,
            null,
            FepOrdStatus.PENDING,
            null,
            null,
            null,
            null,
            Instant.parse("2026-03-01T10:10:00Z"),
            "execution report is still pending",
            null,
            null,
            null
        ),
        Arguments.of(
            "rejected",
            CL_ORD_ID_15,
            STATUS_REJECTED_RESPONSE_FIXTURE,
            Map.of(
                "clOrdId", CL_ORD_ID_15,
                "transactTime", "2026-03-01T10:06:00Z",
                "queryTime", "2026-03-01T10:10:00Z",
                "rejectReason", "INSUFFICIENT_FUNDS"
            ),
            null,
            FepExecType.REJECTED,
            FepOrdStatus.REJECTED,
            null,
            null,
            null,
            Instant.parse("2026-03-01T10:06:00Z"),
            Instant.parse("2026-03-01T10:10:00Z"),
            null,
            "INSUFFICIENT_FUNDS",
            null,
            null
        ),
        Arguments.of(
            "canceled-full",
            CL_ORD_ID_16,
            STATUS_CANCELED_RESPONSE_FIXTURE,
            Map.of(
                "clOrdId", CL_ORD_ID_16,
                "transactTime", "2026-03-01T10:06:00Z",
                "queryTime", "2026-03-01T10:10:00Z"
            ),
            null,
            FepExecType.CANCELED,
            FepOrdStatus.CANCELED,
            null,
            null,
            null,
            Instant.parse("2026-03-01T10:06:00Z"),
            Instant.parse("2026-03-01T10:10:00Z"),
            null,
            null,
            10L,
            null
        ),
        Arguments.of(
            "canceled-partial",
            CL_ORD_ID_18,
            STATUS_CANCELED_PARTIAL_RESPONSE_FIXTURE,
            Map.of(
                "clOrdId", CL_ORD_ID_18,
                "fepOrderId", "FEP-KRX-" + CL_ORD_ID_18,
                "transactTime", "2026-03-01T10:06:00Z",
                "queryTime", "2026-03-01T10:10:00Z"
            ),
            "FEP-KRX-" + CL_ORD_ID_18,
            FepExecType.CANCELED,
            FepOrdStatus.CANCELED,
            5L,
            72000L,
            null,
            Instant.parse("2026-03-01T10:06:00Z"),
            Instant.parse("2026-03-01T10:10:00Z"),
            null,
            null,
            5L,
            null
        ),
        Arguments.of(
            "malformed",
            CL_ORD_ID_17,
            STATUS_MALFORMED_RESPONSE_FIXTURE,
            Map.of(
                "clOrdId", CL_ORD_ID_17,
                "queryTime", "2026-03-01T10:10:00Z",
                "message", "FIX ExecutionReport parse failed; manual review required",
                "parseError", "PARSE_ERROR:Tag 39 missing or invalid"
            ),
            null,
            null,
            FepOrdStatus.MALFORMED,
            null,
            null,
            null,
            null,
            Instant.parse("2026-03-01T10:10:00Z"),
            "FIX ExecutionReport parse failed; manual review required",
            null,
            null,
            "PARSE_ERROR:Tag 39 missing or invalid"
        )
    );
  }
}
