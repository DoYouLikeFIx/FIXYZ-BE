package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataEventSink;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.web.client.RestClient;

class KisLiveMarketDataBootstrapTest {

  @Test
  void shouldStartConfiguredSymbolsWhenKisLiveModeEnabled() throws Exception {
    RecordingKisLiveMarketDataAdapter adapter = new RecordingKisLiveMarketDataAdapter();
    KisLiveMarketDataBootstrap bootstrap = new KisLiveMarketDataBootstrap(liveProperties(), adapter);

    bootstrap.run(new DefaultApplicationArguments(new String[0]));

    assertThat(adapter.startedSubscriptions()).extracting(MarketDataSubscriptionSpec::subscriptionId)
        .containsExactly("kis-live-bootstrap-005930", "kis-live-bootstrap-000660");
    assertThat(adapter.startedSubscriptions()).extracting(MarketDataSubscriptionSpec::sourceMode)
        .containsOnly(FepQuoteSourceMode.LIVE);
  }

  @Test
  void shouldDoNothingWhenKisLiveModeIsDisabled() throws Exception {
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("NONE");
    properties.setSourceMode("REPLAY");
    RecordingKisLiveMarketDataAdapter adapter = new RecordingKisLiveMarketDataAdapter();
    KisLiveMarketDataBootstrap bootstrap = new KisLiveMarketDataBootstrap(properties, adapter);

    bootstrap.run(new DefaultApplicationArguments(new String[0]));

    assertThat(adapter.startedSubscriptions()).isEmpty();
  }

  private FepMarketDataProperties liveProperties() {
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("KIS");
    properties.setSourceMode("LIVE");
    properties.getKis().setEnv("paper");
    properties.getKis().getWs().setTrId("H0STCNT0");
    properties.getKis().getWs().setCusttype("P");
    properties.getKis().getWs().setSymbols(List.of("005930", "000660"));
    return properties;
  }

  private static final class RecordingKisLiveMarketDataAdapter extends KisLiveMarketDataAdapter {

    private final List<MarketDataSubscriptionSpec> startedSubscriptions = new ArrayList<>();

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
    public synchronized void start(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink) {
      startedSubscriptions.add(subscriptionSpec);
    }

    @Override
    public synchronized void stop(String subscriptionId) {
    }

    private List<MarketDataSubscriptionSpec> startedSubscriptions() {
      return startedSubscriptions;
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
