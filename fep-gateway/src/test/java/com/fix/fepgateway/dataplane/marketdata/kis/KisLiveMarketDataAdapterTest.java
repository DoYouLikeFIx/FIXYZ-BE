package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.MarketDataEventSink;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KisLiveMarketDataAdapterTest {

  private static final MarketDataEventSink NO_OP_SINK = event -> {
  };

  @Test
  void shouldConnectOnceAndSendSubscribePayload() throws Exception {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient);

    adapter.start(subscription("sub-005930", "005930"), NO_OP_SINK);
    adapter.start(subscription("sub-000660", "000660"), NO_OP_SINK);

    assertThat(sessionClient.connectedUris())
        .containsExactly(URI.create("ws://ops.koreainvestment.com:31000"));
    assertThat(sessionClient.session().sentPayloads()).hasSize(2);
    assertThat(new ObjectMapper().readTree(sessionClient.session().sentPayloads().get(0))
        .path("body").path("input").path("tr_key").asText()).isEqualTo("005930");
    assertThat(new ObjectMapper().readTree(sessionClient.session().sentPayloads().get(1))
        .path("body").path("input").path("tr_key").asText()).isEqualTo("000660");
  }

  @Test
  void shouldAvoidDuplicateSubscribeForSameRemoteSubscription() {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient);

    adapter.start(subscription("sub-1", "005930"), NO_OP_SINK);
    adapter.start(subscription("sub-2", "005930"), NO_OP_SINK);

    assertThat(sessionClient.session().sentPayloads()).hasSize(1);
  }

  @Test
  void shouldSendUnsubscribeAndCloseSessionWhenLastSubscriptionStops() throws Exception {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient);

    adapter.start(subscription("sub-005930", "005930"), NO_OP_SINK);
    adapter.stop("sub-005930");

    assertThat(sessionClient.session().sentPayloads()).hasSize(2);
    assertThat(new ObjectMapper().readTree(sessionClient.session().sentPayloads().get(1))
        .path("header").path("tr_type").asText()).isEqualTo("2");
    assertThat(sessionClient.session().closed()).isTrue();
  }

  @Test
  void shouldCacheDecryptionContextFromSubscribeSuccessMessage() {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient);

    adapter.start(subscription("sub-005930", "005930"), NO_OP_SINK);
    sessionClient.session().emit("""
        {
          "header": {
            "tr_id": "H0STCNT0"
          },
          "body": {
            "msg1": "SUBSCRIBE SUCCESS",
            "output": {
              "key": "12345678901234567890123456789012",
              "iv": "1234567890123456"
            }
          }
        }
        """);

    assertThat(adapter.findDecryptionContext("H0STCNT0"))
        .hasValueSatisfying(context -> assertThat(context.iv()).isEqualTo("1234567890123456"));
  }

  private KisLiveMarketDataAdapter newAdapter(FakeKisWebSocketSessionClient sessionClient) {
    return new KisLiveMarketDataAdapter(
        liveProperties(),
        new KisApprovalKeyService(new StubKisApprovalClient("approval-key-001")),
        sessionClient,
        new KisWebSocketPayloadFactory(new ObjectMapper()),
        new KisWebSocketControlMessageParser(new ObjectMapper()),
        new KisDecryptionContextStore()
    );
  }

  private FepMarketDataProperties liveProperties() {
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("KIS");
    properties.setSourceMode("LIVE");
    properties.getKis().setEnv("paper");
    properties.getKis().setAppKey("paper-app-key");
    properties.getKis().setAppSecret("paper-app-secret");
    properties.getKis().getWs().setTrId("H0STCNT0");
    properties.getKis().getWs().setCusttype("P");
    properties.getKis().getWs().setSymbols(List.of("005930", "000660"));
    return properties;
  }

  private MarketDataSubscriptionSpec subscription(String subscriptionId, String symbol) {
    return new MarketDataSubscriptionSpec(
        subscriptionId,
        "KIS",
        symbol,
        FepQuoteSourceMode.LIVE,
        "H0STCNT0",
        symbol
    );
  }

  private static final class StubKisApprovalClient extends KisApprovalClient {

    private final String approvalKeyValue;

    private StubKisApprovalClient(String approvalKeyValue) {
      super(RestClient.builder().build(), new FepMarketDataProperties());
      this.approvalKeyValue = approvalKeyValue;
    }

    @Override
    public KisApprovalKey issueApprovalKey() {
      return new KisApprovalKey(approvalKeyValue, Instant.parse("2026-03-19T00:00:00Z"));
    }
  }

  private static final class FakeKisWebSocketSessionClient implements KisWebSocketSessionClient {

    private final List<URI> connectedUris = new ArrayList<>();
    private final AtomicReference<FakeKisWebSocketSession> session = new AtomicReference<>();

    @Override
    public KisWebSocketSession connect(URI uri, java.util.function.Consumer<String> inboundTextHandler) {
      connectedUris.add(uri);
      FakeKisWebSocketSession fakeSession = new FakeKisWebSocketSession(inboundTextHandler);
      session.set(fakeSession);
      return fakeSession;
    }

    private List<URI> connectedUris() {
      return connectedUris;
    }

    private FakeKisWebSocketSession session() {
      return session.get();
    }
  }

  private static final class FakeKisWebSocketSession implements KisWebSocketSession {

    private final java.util.function.Consumer<String> inboundTextHandler;
    private final List<String> sentPayloads = new ArrayList<>();
    private boolean open = true;
    private boolean closed;

    private FakeKisWebSocketSession(java.util.function.Consumer<String> inboundTextHandler) {
      this.inboundTextHandler = inboundTextHandler;
    }

    @Override
    public void sendText(String payload) {
      sentPayloads.add(payload);
    }

    @Override
    public void close() {
      open = false;
      closed = true;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    private List<String> sentPayloads() {
      return sentPayloads;
    }

    private boolean closed() {
      return closed;
    }

    private void emit(String message) {
      inboundTextHandler.accept(message);
    }
  }
}
