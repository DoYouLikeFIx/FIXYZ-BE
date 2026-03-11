package com.fix.channel.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.channel.vo.AccountPositionQueryCommand;
import com.fix.channel.vo.AccountPositionResult;
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

  private AccountPositionQueryCommand command() {
    return AccountPositionQueryCommand.of(ACCOUNT_ID, MEMBER_ID, SYMBOL);
  }
}
