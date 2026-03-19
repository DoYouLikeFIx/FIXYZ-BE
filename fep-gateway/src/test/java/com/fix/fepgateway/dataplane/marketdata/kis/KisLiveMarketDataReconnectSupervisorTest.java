package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataEventSink;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KisLiveMarketDataReconnectSupervisorTest {

  @Test
  void shouldInvokeReconnectSupervisorWhenKisStreamingModeEnabled() {
    RecordingKisLiveMarketDataAdapter adapter = new RecordingKisLiveMarketDataAdapter();
    KisLiveMarketDataReconnectSupervisor supervisor =
        new KisLiveMarketDataReconnectSupervisor(kisStreamingProperties(), adapter);

    supervisor.supervise();

    assertThat(adapter.reconnectCalls()).isEqualTo(1);
  }

  @Test
  void shouldSkipReconnectSupervisorWhenKisStreamingModeDisabled() {
    RecordingKisLiveMarketDataAdapter adapter = new RecordingKisLiveMarketDataAdapter();
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("REPLAY");
    properties.setSourceMode("REPLAY");
    KisLiveMarketDataReconnectSupervisor supervisor = new KisLiveMarketDataReconnectSupervisor(properties, adapter);

    supervisor.supervise();

    assertThat(adapter.reconnectCalls()).isZero();
  }

  private FepMarketDataProperties kisStreamingProperties() {
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("KIS");
    properties.setSourceMode("LIVE");
    properties.getKis().setEnv("paper");
    properties.getKis().setAppKey("paper-app-key");
    properties.getKis().setAppSecret("paper-app-secret");
    properties.getKis().getWs().setTrId("H0STCNT0");
    properties.getKis().getWs().setCusttype("P");
    return properties;
  }

  private static final class RecordingKisLiveMarketDataAdapter extends KisLiveMarketDataAdapter {

    private int reconnectCalls;

    private RecordingKisLiveMarketDataAdapter() {
      super(
          new FepMarketDataProperties(),
          new KisApprovalKeyService(new NoOpKisApprovalClient()),
          (uri, inboundTextHandler) -> new NoOpSession(),
          new KisWebSocketPayloadFactory(new ObjectMapper()),
          new KisWebSocketControlMessageParser(new ObjectMapper()),
          new KisDecryptionContextStore(),
          new KisH0stcnt0RecordParser(new KisRealtimeFrameParser(), new KisPayloadDecryptor()),
          new KisH0stcnt0EventMapper(),
          new NoOpPersistencePort()
      );
    }

    @Override
    synchronized boolean reconnectIfNecessary() {
      reconnectCalls += 1;
      return true;
    }

    @Override
    public synchronized void start(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink) {
    }

    @Override
    public synchronized void stop(String subscriptionId) {
    }

    private int reconnectCalls() {
      return reconnectCalls;
    }
  }

  private static final class NoOpSession implements KisWebSocketSession {
    @Override
    public void sendText(String payload) {
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isOpen() {
      return true;
    }
  }

  private static final class NoOpKisApprovalClient extends KisApprovalClient {
    private NoOpKisApprovalClient() {
      super(RestClient.builder().build(), new FepMarketDataProperties());
    }

    @Override
    public KisApprovalKey issueApprovalKey() {
      return new KisApprovalKey("unused", Instant.parse("2026-03-19T00:00:00Z"));
    }
  }

  private static final class NoOpPersistencePort implements LiveMarketDataPersistencePort {
    @Override
    public void activateSubscription(MarketDataSubscriptionSpec subscriptionSpec) {
    }

    @Override
    public void deactivateSubscription(MarketDataSubscriptionSpec subscriptionSpec) {
    }

    @Override
    public void persistSnapshot(MarketDataSubscriptionSpec subscriptionSpec, NormalizedQuoteEvent event) {
    }
  }
}
