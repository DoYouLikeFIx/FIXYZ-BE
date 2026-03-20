package com.fix.fepgateway.dataplane.fix;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepOrderType;
import com.fix.common.fep.FepSecurityExchange;
import com.fix.common.fep.FepSide;
import com.fix.fepgateway.vo.GatewayExecutionOutcome;
import com.fix.fepgateway.vo.GatewayOrderSubmitCommand;
import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class FixDataPlaneServiceTest {

  private static final String CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174355";

  private WireMockServer wireMockServer;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void shouldTranslateTimeoutChaosActionIntoSubmitTimeout() {
    wireMockServer.stubFor(get(urlPathEqualTo("/api/v1/ping"))
        .withQueryParam("symbol", equalTo("005930"))
        .withQueryParam("exchange", equalTo("KRX"))
        .withQueryParam("amount", equalTo("140200"))
        .willReturn(okJson("""
            {
              "service": "fep-simulator",
              "status": "ok",
              "chaosAction": "TIMEOUT"
            }
            """)));

    FixDataPlaneService service = new FixDataPlaneService(restClient(), true);

    BusinessException exception = catchThrowableOfType(
        () -> service.sendNewOrder(limitOrderSubmit()),
        BusinessException.class
    );

    assertThat(exception).isNotNull();
    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FEP_ACK_TIMEOUT);
    assertThat(exception.getMetadata()).isNotNull();
    assertThat(exception.getMetadata().userMessageKey()).isEqualTo("error.fep.timeout");
    assertThat(exception.getMetadata().operatorCode()).isEqualTo("TIMEOUT");
  }

  @Test
  void shouldSkipChaosProbeWhenDisabled() {
    FixDataPlaneService service = new FixDataPlaneService(restClient(), false);

    GatewayExecutionOutcome outcome = service.sendNewOrder(limitOrderSubmit());

    assertThat(outcome.ordStatus()).isEqualTo(com.fix.common.fep.FepOrdStatus.FILLED);
    assertThat(outcome.executedPrice()).isEqualTo(70_100L);
    wireMockServer.verify(0, getRequestedFor(urlPathMatching("/api/v1/ping.*")));
  }

  @Test
  void shouldFallBackToNormalSubmitWhenChaosProbeIsUnavailable() {
    FixDataPlaneService service = new FixDataPlaneService(
        RestClient.builder().baseUrl("http://127.0.0.1:1").build(),
        true
    );

    GatewayExecutionOutcome outcome = service.sendNewOrder(limitOrderSubmit());

    assertThat(outcome.ordStatus()).isEqualTo(com.fix.common.fep.FepOrdStatus.FILLED);
    assertThat(outcome.executedPrice()).isEqualTo(70_100L);
  }

  @Test
  void shouldFallBackToNormalSubmitWhenChaosProbeAmountOverflows() {
    FixDataPlaneService service = new FixDataPlaneService(restClient(), true);

    GatewayExecutionOutcome outcome = service.sendNewOrder(limitOrderSubmit(Long.MAX_VALUE, 2L));

    assertThat(outcome.ordStatus()).isEqualTo(com.fix.common.fep.FepOrdStatus.FILLED);
    assertThat(outcome.executedPrice()).isEqualTo(2L);
    wireMockServer.verify(0, getRequestedFor(urlPathMatching("/api/v1/ping.*")));
  }

  private RestClient restClient() {
    return RestClient.builder()
        .baseUrl(wireMockServer.baseUrl())
        .build();
  }

  private GatewayOrderSubmitCommand limitOrderSubmit() {
    return limitOrderSubmit(2L, 70_100L);
  }

  private GatewayOrderSubmitCommand limitOrderSubmit(long qty, long price) {
    return new GatewayOrderSubmitCommand(
        CL_ORD_ID,
        "ACC-001",
        "005930",
        FepSecurityExchange.KRX,
        FepSide.BUY,
        FepOrderType.LIMIT,
        qty,
        price,
        null,
        null,
        null,
        null,
        "KRW",
        "ref-355"
    );
  }
}
