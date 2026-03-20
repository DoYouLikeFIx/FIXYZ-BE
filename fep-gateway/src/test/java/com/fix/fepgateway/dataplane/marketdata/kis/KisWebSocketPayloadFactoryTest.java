package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import org.junit.jupiter.api.Test;

class KisWebSocketPayloadFactoryTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final KisWebSocketPayloadFactory payloadFactory = new KisWebSocketPayloadFactory(objectMapper);

  @Test
  void shouldCreateSubscribePayloadUsingOfficialBodyInputShape() throws Exception {
    MarketDataSubscriptionSpec subscriptionSpec = new MarketDataSubscriptionSpec(
        "sub-005930",
        "KIS",
        "005930",
        FepQuoteSourceMode.LIVE,
        "H0STCNT0",
        "005930"
    );

    String payload = payloadFactory.createSubscribePayload("approval-key-001", "P", subscriptionSpec);

    assertThat(objectMapper.readTree(payload)).isEqualTo(objectMapper.readTree("""
        {
          "header": {
            "approval_key": "approval-key-001",
            "custtype": "P",
            "tr_type": "1",
            "content-type": "utf-8"
          },
          "body": {
            "input": {
              "tr_id": "H0STCNT0",
              "tr_key": "005930"
            }
          }
        }
        """));
  }

  @Test
  void shouldCreateUnsubscribePayloadWithTrTypeTwo() throws Exception {
    MarketDataSubscriptionSpec subscriptionSpec = new MarketDataSubscriptionSpec(
        "sub-005930",
        "KIS",
        "005930",
        FepQuoteSourceMode.LIVE,
        "H0STCNT0",
        "005930"
    );

    String payload = payloadFactory.createUnsubscribePayload("approval-key-001", "P", subscriptionSpec);

    assertThat(objectMapper.readTree(payload).path("header").path("tr_type").asText()).isEqualTo("2");
  }
}
