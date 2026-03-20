package com.fix.channel.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.channel.vo.AdminAccountStatusTransitionCommand;
import com.fix.channel.vo.AdminAccountStatusTransitionResult;
import com.fix.channel.vo.AccountPositionQueryCommand;
import com.fix.channel.vo.AccountPositionsQueryCommand;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.channel.vo.AccountSummaryQueryCommand;
import com.fix.channel.vo.AdminOrderReplayCommand;
import com.fix.channel.vo.AccountOrderHistoryQueryCommand;
import com.fix.channel.vo.AccountOrderHistoryResult;
import com.fix.channel.vo.OrderReplayResult;
import com.fix.channel.vo.OrderRequeryResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CorebankClientTest {

  private static final Long ACCOUNT_ID = 1L;
  private static final Long MEMBER_ID = 301L;
  private static final String SYMBOL = "005930";
  private static final int PAGE = 0;
  private static final int SIZE = 20;

  private WireMockServer wireMockServer;
  private CorebankClient corebankClient;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
    corebankClient = new CorebankClient(
        RestClient.builder().baseUrl("http://127.0.0.1:" + wireMockServer.port()).build(),
        "test-secret"
    );
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void shouldMap504ToCoreDependencyTimeout() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("symbol", equalTo(SYMBOL))
        .willReturn(aResponse()
            .withStatus(504)
            .withHeader("Content-Type", "text/plain")
            .withBody("upstream timeout")));

    assertThatThrownBy(() -> corebankClient.getAccountPosition(command(), "trace-core-901"))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.CORE_DEPENDENCY_TIMEOUT);
  }

  @Test
  void shouldMap503ToCoreDependencyUnavailable() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("symbol", equalTo(SYMBOL))
        .willReturn(aResponse()
            .withStatus(503)
            .withHeader("Content-Type", "text/plain")
            .withBody("service unavailable")));

    assertThatThrownBy(() -> corebankClient.getAccountPosition(command(), "trace-core-902"))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.CORE_DEPENDENCY_UNAVAILABLE);
  }

  @Test
  void shouldMapRequeryResponseAndForwardAttemptCount() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/orders/123e4567-e89b-42d3-a456-426614174300/requery"))
        .withQueryParam("attemptCount", equalTo("2"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "orderId": 99,
                    "clOrdId": "123e4567-e89b-42d3-a456-426614174300",
                    "status": "FILLED",
                    "externalSyncStatus": "CONFIRMED",
                    "executionResult": "FILLED",
                    "executedQty": 1.0000,
                    "leavesQty": 0.0000,
                    "executedPrice": 72000.0000,
                    "externalOrderId": "FEP-300",
                    "executedAt": "2026-03-10T00:00:00Z",
                    "canceledAt": "2026-03-10T00:01:00Z",
                    "message": null,
                    "retriable": false,
                    "escalationRequired": false,
                    "attemptCount": 2,
                    "maxRetryCount": 5
                  }
                }
                """)));

    OrderRequeryResult result = corebankClient.requeryOrder(
        "123e4567-e89b-42d3-a456-426614174300",
        2,
        "trace-requery-2"
    );

    assertThat(result.getStatus()).isEqualTo("FILLED");
    assertThat(result.getExternalSyncStatus()).isEqualTo("CONFIRMED");
    assertThat(result.getAttemptCount()).isEqualTo(2);
    assertThat(result.getMaxRetryCount()).isEqualTo(5);
    assertThat(result.getCanceledAt()).isEqualTo(Instant.parse("2026-03-10T00:01:00Z"));

    wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/v1/orders/123e4567-e89b-42d3-a456-426614174300/requery"))
        .withQueryParam("attemptCount", equalTo("2"))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("trace-requery-2")));
  }

  @Test
  void shouldMapReplayResponseAndForwardGovernancePayload() {
    wireMockServer.stubFor(post(urlPathEqualTo("/internal/v1/orders/123e4567-e89b-42d3-a456-426614174301/replay"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "clOrdId": "123e4567-e89b-42d3-a456-426614174301",
                    "finalStatus": "COMPLETED",
                    "executionResult": "FILLED",
                    "executionSource": "VIRTUAL_FILL",
                    "executedQty": 10.0000,
                    "leavesQty": 0.0000,
                    "executedPrice": 72000.0000,
                    "externalOrderId": "FEP-301",
                    "externalSyncStatus": "CONFIRMED",
                    "executedAt": "2026-03-10T00:00:00Z",
                    "processedBy": "123e4567-e89b-42d3-a456-426614174399",
                    "processedAt": "2026-03-10T00:05:00Z"
                  }
                }
                """)));

    OrderReplayResult result = corebankClient.replayOrder(
        "123e4567-e89b-42d3-a456-426614174301",
        AdminOrderReplayCommand.of(
            "APPROVE",
            "123e4567-e89b-42d3-a456-426614174355",
            "OPS-INC-1",
            "KRX outage resolved after manual exchange confirmation",
            72000L
        ),
        "123e4567-e89b-42d3-a456-426614174399",
        "trace-replay-301"
    );

    assertThat(result.getFinalStatus()).isEqualTo("COMPLETED");
    assertThat(result.getExecutionSource()).isEqualTo("VIRTUAL_FILL");
    assertThat(result.getProcessedBy()).isEqualTo("123e4567-e89b-42d3-a456-426614174399");
    assertThat(result.getProcessedAt()).isEqualTo(Instant.parse("2026-03-10T00:05:00Z"));

    wireMockServer.verify(postRequestedFor(urlPathEqualTo("/internal/v1/orders/123e4567-e89b-42d3-a456-426614174301/replay"))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("trace-replay-301"))
        .withRequestBody(matching(".*\"manualDecision\":\"APPROVE\".*"))
        .withRequestBody(matching(".*\"operatorId\":\"123e4567-e89b-42d3-a456-426614174399\".*"))
        .withRequestBody(matching(".*\"approvedBy\":\"123e4567-e89b-42d3-a456-426614174355\".*")));
  }

  @Test
  void shouldNormalizeReplayConflictCodeToOrd009() {
    wireMockServer.stubFor(post(urlPathEqualTo("/internal/v1/orders/123e4567-e89b-42d3-a456-426614174301/replay"))
        .willReturn(aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "9009",
                  "message": "replay target must be ESCALATED",
                  "details": {
                    "upstreamStatus": "ESCALATED"
                  }
                }
                """)));

    assertThatThrownBy(() -> corebankClient.replayOrder(
        "123e4567-e89b-42d3-a456-426614174301",
        AdminOrderReplayCommand.of(
            "APPROVE",
            "123e4567-e89b-42d3-a456-426614174355",
            "OPS-INC-1",
            "KRX outage resolved after manual exchange confirmation",
            72000L
        ),
        "123e4567-e89b-42d3-a456-426614174399",
        "trace-replay-ord-009"
    ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED);
          assertThat(businessException.getMessage()).isEqualTo("replay target must be ESCALATED");
          assertThat(businessException.getDetails()).isEqualTo(Map.of("upstreamStatus", "ESCALATED"));
        });
  }

  @Test
  void shouldNormalizeReplayConflictWithoutMachineCodeToOrd009() {
    wireMockServer.stubFor(post(urlPathEqualTo("/internal/v1/orders/123e4567-e89b-42d3-a456-426614174301/replay"))
        .willReturn(aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "message": "corebank replay state drift detected",
                  "details": {
                    "corebankStatus": "FILLED"
                  }
                }
                """)));

    assertThatThrownBy(() -> corebankClient.replayOrder(
        "123e4567-e89b-42d3-a456-426614174301",
        AdminOrderReplayCommand.of(
            "APPROVE",
            "123e4567-e89b-42d3-a456-426614174355",
            "OPS-INC-1",
            "KRX outage resolved after manual exchange confirmation",
            72000L
        ),
        "123e4567-e89b-42d3-a456-426614174399",
        "trace-replay-ord-009-generic"
    ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED);
          assertThat(businessException.getMessage()).isEqualTo("corebank replay state drift detected");
          assertThat(businessException.getDetails()).isEqualTo(Map.of("corebankStatus", "FILLED"));
        });
  }

  @Test
  void shouldFallbackToInternalErrorForNon503504WithoutMachineCode() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("symbol", equalTo(SYMBOL))
        .willReturn(aResponse()
            .withStatus(404)
            .withHeader("Content-Type", "text/plain")
            .withBody("not found")));

    assertThatThrownBy(() -> corebankClient.getAccountPosition(command(), "trace-core-internal"))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.INTERNAL_ERROR);
  }

  @Test
  void shouldPreserveKnownCorebankMachineCodeWhenProvided() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("symbol", equalTo(SYMBOL))
        .willReturn(aResponse()
            .withStatus(403)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "AUTH-005",
                  "message": "forbidden account ownership",
                  "path": "/internal/v1/accounts/1/positions",
                  "correlationId": "trace-core-auth-005",
                  "details": {
                    "accountId": 1,
                    "memberId": 301
                  },
                  "timestamp": "2026-03-10T00:00:00Z"
                }
                """)));

    assertThatThrownBy(() -> corebankClient.getAccountPosition(command(), "trace-channel-auth-005"))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP);
          assertThat(businessException.getMessage()).isEqualTo("forbidden account ownership");
          assertThat(businessException.getDetails()).isEqualTo(Map.of("accountId", 1, "memberId", 301));
        });
  }

  @Test
  void shouldUseAliasFieldsWhenCanonicalFieldsAreMissing() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("symbol", equalTo(SYMBOL))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "accountId": 1,
                    "memberId": 301,
                    "symbol": "005930",
                    "quantity": 120.0000,
                    "availableQty": 90.0000,
                    "availableBalance": 500000.0000,
                    "currency": "KRW",
                    "asOf": "2026-03-10T00:00:00Z"
                  }
                }
                """)));

    AccountPositionResult result = corebankClient.getAccountPosition(command(), "trace-channel-alias");

    assertThat(result.getQuantity()).isEqualByComparingTo("120.0000");
    assertThat(result.getAvailableQuantity()).isEqualByComparingTo("90.0000");
    assertThat(result.getBalance()).isEqualByComparingTo("500000.0000");
    assertThat(result.getCurrency()).isEqualTo("KRW");
    assertThat(result.getAsOf()).isEqualTo(Instant.parse("2026-03-10T00:00:00Z"));

    wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("symbol", equalTo(SYMBOL))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("trace-channel-alias")));
  }

  @Test
  void shouldMapQuoteMetadataWhenCorebankProvidesCanonicalFields() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("symbol", equalTo(SYMBOL))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "accountId": 1,
                    "memberId": 301,
                    "symbol": "005930",
                    "quantity": 120.0000,
                    "availableQuantity": 90.0000,
                    "balance": 500000.0000,
                    "currency": "KRW",
                    "asOf": "2026-03-10T00:00:00Z",
                    "marketPrice": 72100.0000,
                    "quoteSnapshotId": "qsnap-005930-live-001",
                    "quoteAsOf": "2026-03-10T00:00:59Z",
                    "quoteSourceMode": "LIVE"
                  }
                }
                """)));

    AccountPositionResult result = corebankClient.getAccountPosition(command(), "trace-channel-quote");

    assertThat(result.getMarketPrice()).isEqualByComparingTo("72100.0000");
    assertThat(result.getQuoteSnapshotId()).isEqualTo("qsnap-005930-live-001");
    assertThat(result.getQuoteAsOf()).isEqualTo(Instant.parse("2026-03-10T00:00:59Z"));
    assertThat(result.getQuoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
  }

  @Test
  void shouldMapAccountSummaryResponse() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/summary"))
        .withQueryParam("memberId", equalTo("301"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "accountId": 1,
                    "memberId": 301,
                    "symbol": "",
                    "quantity": 0.0000,
                    "availableQuantity": 0.0000,
                    "availableQty": 0.0000,
                    "balance": 1000000.0000,
                    "availableBalance": 1000000.0000,
                    "currency": "KRW",
                    "asOf": "2026-03-10T00:00:00Z"
                  }
                }
                """)));

    AccountPositionResult result = corebankClient.getAccountSummary(summaryCommand(), "trace-summary");

    assertThat(result.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.getMemberId()).isEqualTo(MEMBER_ID);
    assertThat(result.getSymbol()).isEmpty();
    assertThat(result.getQuantity()).isEqualByComparingTo("0.0000");
    assertThat(result.getAvailableQuantity()).isEqualByComparingTo("0.0000");
    assertThat(result.getBalance()).isEqualByComparingTo("1000000.0000");
    assertThat(result.getCurrency()).isEqualTo("KRW");
    assertThat(result.getMarketPrice()).isNull();
    assertThat(result.getQuoteSnapshotId()).isNull();
    assertThat(result.getQuoteAsOf()).isNull();
    assertThat(result.getQuoteSourceMode()).isNull();

    wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/summary"))
        .withQueryParam("memberId", equalTo("301"))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("trace-summary")));
  }

  @Test
  void shouldMapOwnedPositionListResponse() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/positions/list"))
        .withQueryParam("memberId", equalTo("301"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": [
                    {
                      "accountId": 1,
                      "memberId": 301,
                      "symbol": "000660",
                      "quantity": 15.0000,
                      "availableQuantity": 7.0000,
                      "availableQty": 7.0000,
                      "balance": 98500000.0000,
                      "availableBalance": 98500000.0000,
                      "currency": "KRW",
                      "asOf": "2026-03-10T00:00:00Z",
                      "marketPrice": 120250.0000,
                      "quoteSnapshotId": "qsnap-000660-live-001",
                      "quoteAsOf": "2026-03-10T00:00:58Z",
                      "quoteSourceMode": "LIVE"
                    },
                    {
                      "accountId": 1,
                      "memberId": 301,
                      "symbol": "005930",
                      "quantity": 120.0000,
                      "availableQuantity": 20.0000,
                      "availableQty": 20.0000,
                      "balance": 100000000.0000,
                      "availableBalance": 100000000.0000,
                      "currency": "KRW",
                      "asOf": "2026-03-10T00:01:00Z",
                      "marketPrice": 72050.0000,
                      "quoteSnapshotId": "qsnap-005930-live-001",
                      "quoteAsOf": "2026-03-10T00:00:59Z",
                      "quoteSourceMode": "LIVE"
                    }
                  ]
                }
                """)));

    assertThat(corebankClient.getAccountPositions(positionsCommand(), "trace-position-list"))
        .hasSize(2)
        .satisfies(results -> {
          AccountPositionResult first = results.get(0);
          AccountPositionResult second = results.get(1);
          assertThat(first.getSymbol()).isEqualTo("000660");
          assertThat(first.getMarketPrice()).isEqualByComparingTo("120250.0000");
          assertThat(first.getQuoteSnapshotId()).isEqualTo("qsnap-000660-live-001");
          assertThat(first.getQuoteAsOf()).isEqualTo(Instant.parse("2026-03-10T00:00:58Z"));
          assertThat(first.getQuoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
          assertThat(second.getSymbol()).isEqualTo("005930");
          assertThat(second.getMarketPrice()).isEqualByComparingTo("72050.0000");
          assertThat(second.getQuoteSnapshotId()).isEqualTo("qsnap-005930-live-001");
          assertThat(second.getQuoteAsOf()).isEqualTo(Instant.parse("2026-03-10T00:00:59Z"));
          assertThat(second.getQuoteSourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
        });

    wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/positions/list"))
        .withQueryParam("memberId", equalTo("301"))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("trace-position-list")));
  }

  @Test
  void shouldMapAccountOrderHistoryResponse() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/orders"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("20"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "content": [
                      {
                        "symbol": "005930",
                        "side": "BUY",
                        "qty": 2.0000,
                        "unitPrice": 70100.0000,
                        "status": "FILLED",
                        "clOrdId": "123e4567-e89b-42d3-a456-426614174310",
                        "createdAt": "2026-03-10T00:00:00Z"
                      }
                    ],
                    "totalElements": 1,
                    "totalPages": 1,
                    "number": 0,
                    "size": 20
                  }
                }
                """)));

    AccountOrderHistoryResult result = corebankClient.getAccountOrderHistory(historyCommand(), "trace-history");

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getSymbol()).isEqualTo("005930");
    assertThat(result.getContent().get(0).getSymbolName()).isEqualTo("005930");
    assertThat(result.getContent().get(0).getQty()).isEqualByComparingTo("2.0000");
    assertThat(result.getContent().get(0).getUnitPrice()).isEqualByComparingTo("70100.0000");
    assertThat(result.getContent().get(0).getTotalAmount()).isEqualByComparingTo("140200.00000000");
    assertThat(result.getContent().get(0).getClOrdId()).isEqualTo("123e4567-e89b-42d3-a456-426614174310");
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.getNumber()).isEqualTo(0);
    assertThat(result.getSize()).isEqualTo(20);

    wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/orders"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("20"))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("trace-history")));
  }

  @Test
  void shouldMapHistory503ToCoreDependencyUnavailable() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/orders"))
        .withQueryParam("memberId", equalTo("301"))
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("20"))
        .willReturn(aResponse().withStatus(503).withBody("service unavailable")));

    assertThatThrownBy(() -> corebankClient.getAccountOrderHistory(historyCommand(), "trace-history-503"))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.CORE_DEPENDENCY_UNAVAILABLE);
  }

  @Test
  void shouldMapAccountStatusTransitionResponse() {
    wireMockServer.stubFor(patch(urlPathEqualTo("/internal/v1/accounts/1/status"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "accountId": 1,
                    "memberId": 301,
                    "previousStatus": "ACTIVE",
                    "newStatus": "FROZEN",
                    "changed": true,
                    "eventId": 9001,
                    "reason": "risk-control",
                    "actor": "ops-admin",
                    "context": "ticket=FIX-43",
                    "asOf": "2026-03-10T00:00:00Z"
                  }
                }
                """)));

    AdminAccountStatusTransitionResult result = corebankClient.transitionAccountStatus(
        statusTransitionCommand("FROZEN"),
        "trace-status-transition"
    );

    assertThat(result.getAccountId()).isEqualTo(1L);
    assertThat(result.getMemberId()).isEqualTo(301L);
    assertThat(result.getPreviousStatus()).isEqualTo("ACTIVE");
    assertThat(result.getNewStatus()).isEqualTo("FROZEN");
    assertThat(result.isChanged()).isTrue();
    assertThat(result.getEventId()).isEqualTo(9001L);
    assertThat(result.getReason()).isEqualTo("risk-control");
    assertThat(result.getActor()).isEqualTo("ops-admin");
    assertThat(result.getContext()).isEqualTo("ticket=FIX-43");
    assertThat(result.getAsOf()).isEqualTo(Instant.parse("2026-03-10T00:00:00Z"));

    wireMockServer.verify(patchRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/status"))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("trace-status-transition")));
  }

  @Test
  void shouldMapStatusTransition403ToOwnershipError() {
    wireMockServer.stubFor(patch(urlPathEqualTo("/internal/v1/accounts/1/status"))
        .willReturn(aResponse()
            .withStatus(403)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "code": "AUTH-005",
                  "message": "forbidden account ownership",
                  "path": "/internal/v1/accounts/1/status",
                  "correlationId": "trace-core-auth-005",
                  "timestamp": "2026-03-10T00:00:00Z"
                }
                """)));

    assertThatThrownBy(() -> corebankClient.transitionAccountStatus(
        statusTransitionCommand("FROZEN"),
        "trace-channel-auth-005"
    ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP);
          assertThat(businessException.getMessage()).isEqualTo("forbidden account ownership");
        });
  }

  @Test
  void shouldForwardTraceparentHeaderToCorebank() {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    TraceparentSupport.putInMdc(traceparent);
    try {
      wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/1/positions"))
          .withQueryParam("memberId", equalTo("301"))
          .withQueryParam("symbol", equalTo(SYMBOL))
          .willReturn(aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody("""
                  {
                    "success": true,
                    "data": {
                      "accountId": 1,
                      "memberId": 301,
                      "symbol": "005930",
                      "quantity": 120.0000,
                      "availableQuantity": 90.0000,
                      "balance": 500000.0000,
                      "currency": "KRW",
                      "asOf": "2026-03-10T00:00:00Z"
                    }
                  }
                  """)));

      corebankClient.getAccountPosition(command(), "trace-core-traceparent");

      wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/positions"))
          .withQueryParam("memberId", equalTo("301"))
          .withQueryParam("symbol", equalTo(SYMBOL))
          .withHeader(CommonHeaders.TRACEPARENT, matching("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"))
          .withHeader(CommonHeaders.TRACEPARENT, equalTo(traceparent)));
    } finally {
      CorrelationIdSupport.clearMdc();
    }
  }

  private AccountPositionQueryCommand command() {
    return AccountPositionQueryCommand.of(ACCOUNT_ID, MEMBER_ID, SYMBOL);
  }

  private AccountOrderHistoryQueryCommand historyCommand() {
    return AccountOrderHistoryQueryCommand.of(ACCOUNT_ID, MEMBER_ID, PAGE, SIZE);
  }

  private AdminAccountStatusTransitionCommand statusTransitionCommand(String status) {
    return AdminAccountStatusTransitionCommand.of(
        ACCOUNT_ID,
        MEMBER_ID,
        status,
        "risk-control",
        "ops-admin",
        "ticket=FIX-43"
    );
  }

  private AccountPositionsQueryCommand positionsCommand() {
    return AccountPositionsQueryCommand.of(ACCOUNT_ID, MEMBER_ID);
  }

  private AccountSummaryQueryCommand summaryCommand() {
    return AccountSummaryQueryCommand.of(ACCOUNT_ID, MEMBER_ID);
  }
}
