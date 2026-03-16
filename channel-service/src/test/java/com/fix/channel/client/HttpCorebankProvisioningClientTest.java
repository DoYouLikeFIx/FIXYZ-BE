package com.fix.channel.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CommonHeaders;
import com.fix.common.web.CorrelationIdSupport;
import com.fix.common.web.TraceparentSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpCorebankProvisioningClientTest {

  private WireMockServer wireMockServer;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void shouldForwardInternalSecretAndCorrelationIdHeaders() {
    wireMockServer.stubFor(post(urlEqualTo("/internal/v1/portfolio"))
        .willReturn(aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "accountId": 1001,
                    "accountNumber": "110123456789",
                    "status": "ACTIVE",
                    "idempotent": false,
                    "memberId": 123
                  }
                }
                """)));

    HttpCorebankProvisioningClient client = new HttpCorebankProvisioningClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret"
    );

    CorebankLinkedAccountProfile profile =
        client.provisionDefaultAccount(123L, "M-123", "member123@fix.local", "corr-123");

    assertThat(profile.accountId()).isEqualTo(1001L);
    assertThat(profile.memberId()).isEqualTo(123L);
    assertThat(profile.accountNumber()).isEqualTo("110123456789");

    wireMockServer.verify(postRequestedFor(urlEqualTo("/internal/v1/portfolio"))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("corr-123"))
        .withRequestBody(matching(".*\"memberId\"\\s*:\\s*123.*"))
        .withRequestBody(matching(".*\"memberNo\"\\s*:\\s*\"M-123\".*"))
        .withRequestBody(matching(".*\"email\"\\s*:\\s*\"member123@fix.local\".*")));
  }

  @Test
  void shouldMapCorebankFailureToCoreProvisioningUnavailableCode() {
    wireMockServer.stubFor(post(urlEqualTo("/internal/v1/portfolio"))
        .willReturn(aResponse().withStatus(503)));

    HttpCorebankProvisioningClient client = new HttpCorebankProvisioningClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret"
    );

    assertThatThrownBy(() -> client.provisionDefaultAccount(
        123L,
        "M-123",
        "member123@fix.local",
        "corr-123"
    ))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.CORE_PROVISIONING_UNAVAILABLE);
  }

  @Test
  void shouldFetchDefaultLinkedAccountProfile() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/default"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "accountId": 1001,
                    "memberId": 123,
                    "accountNumber": "110123456789"
                  }
                }
                """)));

    HttpCorebankProvisioningClient client = new HttpCorebankProvisioningClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret"
    );

    CorebankLinkedAccountProfile profile = client.fetchDefaultAccountProfile(123L, "corr-lookup");

    assertThat(profile.accountId()).isEqualTo(1001L);
    assertThat(profile.memberId()).isEqualTo(123L);
    assertThat(profile.accountNumber()).isEqualTo("110123456789");

    wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/v1/accounts/default"))
        .withQueryParam("memberId", equalTo("123"))
        .withHeader("X-Internal-Secret", equalTo("test-secret"))
        .withHeader("X-Correlation-Id", equalTo("corr-lookup")));
  }

  @Test
  void shouldReturnNullWhenNoLinkedAccountProfileExists() {
    wireMockServer.stubFor(get(urlPathEqualTo("/internal/v1/accounts/default"))
        .willReturn(aResponse().withStatus(404)));

    HttpCorebankProvisioningClient client = new HttpCorebankProvisioningClient(
        RestClient.builder(),
        "http://127.0.0.1:" + wireMockServer.port(),
        "test-secret"
    );

    assertThat(client.fetchDefaultAccountProfile(123L, "corr-missing")).isNull();
  }

  @Test
  void shouldForwardTraceparentHeaderToCorebankProvisioning() {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    TraceparentSupport.putInMdc(traceparent);
    try {
      wireMockServer.stubFor(post(urlEqualTo("/internal/v1/portfolio"))
          .willReturn(aResponse()
              .withStatus(201)
              .withHeader("Content-Type", "application/json")
              .withBody("""
                  {
                    "success": true,
                    "data": {
                      "accountId": 1001,
                      "accountNumber": "110123456789",
                      "status": "ACTIVE",
                      "idempotent": false,
                      "memberId": 123
                    }
                  }
                  """)));

      HttpCorebankProvisioningClient client = new HttpCorebankProvisioningClient(
          RestClient.builder(),
          "http://127.0.0.1:" + wireMockServer.port(),
          "test-secret"
      );

      client.provisionDefaultAccount(123L, "M-123", "member123@fix.local", "corr-123");

      wireMockServer.verify(postRequestedFor(urlEqualTo("/internal/v1/portfolio"))
          .withHeader(CommonHeaders.TRACEPARENT, equalTo(traceparent)));
    } finally {
      CorrelationIdSupport.clearMdc();
    }
  }
}
