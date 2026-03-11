package com.fix.channel.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.channel.vo.AccountPositionQueryCommand;
import com.fix.channel.vo.AccountPositionsQueryCommand;
import com.fix.channel.vo.AccountPositionResult;
import com.fix.channel.vo.AccountSummaryQueryCommand;
import com.fix.channel.vo.AccountOrderHistoryQueryCommand;
import com.fix.channel.vo.AccountOrderHistoryResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Instant;
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
                  "timestamp": "2026-03-10T00:00:00Z"
                }
                """)));

    assertThatThrownBy(() -> corebankClient.getAccountPosition(command(), "trace-channel-auth-005"))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN_OWNERSHIP);
          assertThat(businessException.getMessage()).isEqualTo("forbidden account ownership");
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
  void shouldMapAccountPositionsResponse() {
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
                      "quantity": 40.0000,
                      "availableQuantity": 40.0000,
                      "balance": 500000.0000,
                      "currency": "KRW",
                      "asOf": "2026-03-10T00:00:00Z"
                    }
                  ]
                }
                """)));

    var result = corebankClient.getAccountPositions(positionsCommand(), "trace-positions");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getSymbol()).isEqualTo("000660");
    assertThat(result.get(0).getQuantity()).isEqualByComparingTo("40.0000");
    assertThat(result.get(0).getAvailableQuantity()).isEqualByComparingTo("40.0000");
    assertThat(result.get(0).getBalance()).isEqualByComparingTo("500000.0000");

    wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/positions/list"))
        .withQueryParam("memberId", equalTo("301"))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("trace-positions")));
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
                    "balance": 750000.0000,
                    "currency": "KRW",
                    "asOf": "2026-03-10T00:00:00Z"
                  }
                }
                """)));

    var result = corebankClient.getAccountSummary(summaryCommand(), "trace-summary");

    assertThat(result.getSymbol()).isEmpty();
    assertThat(result.getQuantity()).isEqualByComparingTo("0.0000");
    assertThat(result.getAvailableQuantity()).isEqualByComparingTo("0.0000");
    assertThat(result.getBalance()).isEqualByComparingTo("750000.0000");

    wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/1/summary"))
        .withQueryParam("memberId", equalTo("301"))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("trace-summary")));
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

  private AccountPositionQueryCommand command() {
    return AccountPositionQueryCommand.of(ACCOUNT_ID, MEMBER_ID, SYMBOL);
  }

  private AccountOrderHistoryQueryCommand historyCommand() {
    return AccountOrderHistoryQueryCommand.of(ACCOUNT_ID, MEMBER_ID, PAGE, SIZE);
  }

  private AccountPositionsQueryCommand positionsCommand() {
    return AccountPositionsQueryCommand.of(ACCOUNT_ID, MEMBER_ID);
  }

  private AccountSummaryQueryCommand summaryCommand() {
    return AccountSummaryQueryCommand.of(ACCOUNT_ID, MEMBER_ID);
  }
}
