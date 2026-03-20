package com.fix.fepgateway.dataplane.marketdata.kis;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.fepgateway.config.FepMarketDataProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class KisApprovalClientTest {

  private WireMockServer wireMockServer;
  private KisApprovalClient kisApprovalClient;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
    kisApprovalClient = new KisApprovalClient(
        RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl("http://127.0.0.1:" + wireMockServer.port())
            .build(),
        liveProperties()
    );
  }

  @AfterEach
  void tearDown() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void shouldRequestApprovalKeyWithOfficialPayload() {
    wireMockServer.stubFor(post(urlEqualTo(KisApprovalClient.APPROVAL_PATH))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "approval_key": "approval-key-001"
                }
                """)));

    KisApprovalKey approvalKey = kisApprovalClient.issueApprovalKey();

    assertThat(approvalKey.value()).isEqualTo("approval-key-001");
    assertThat(approvalKey.issuedAt()).isNotNull();

    wireMockServer.verify(postRequestedFor(urlEqualTo(KisApprovalClient.APPROVAL_PATH))
        .withHeader("Accept", equalTo("text/plain"))
        .withHeader("charset", equalTo("UTF-8"))
        .withRequestBody(equalToJson("""
            {
              "grant_type": "client_credentials",
              "appkey": "paper-app-key",
              "secretkey": "paper-app-secret"
            }
            """, true, false)));
  }

  @Test
  void shouldFailWhenApprovalKeyIsMissingFromResponse() {
    wireMockServer.stubFor(post(urlEqualTo(KisApprovalClient.APPROVAL_PATH))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "rt_cd": "0",
                  "msg1": "ok"
                }
                """)));

    assertThatThrownBy(() -> kisApprovalClient.issueApprovalKey())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("approval_key");
  }

  @Test
  void shouldFailWhenApprovalEndpointReturnsErrorStatus() {
    wireMockServer.stubFor(post(urlEqualTo(KisApprovalClient.APPROVAL_PATH))
        .willReturn(aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "msg1": "internal error"
                }
                """)));

    assertThatThrownBy(() -> kisApprovalClient.issueApprovalKey())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("status 500");
  }

  private FepMarketDataProperties liveProperties() {
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("KIS");
    properties.setSourceMode("LIVE");
    properties.getKis().setEnv("paper");
    properties.getKis().setAppKey("paper-app-key");
    properties.getKis().setAppSecret("paper-app-secret");
    return properties;
  }
}
