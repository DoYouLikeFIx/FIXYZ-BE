package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataEventSink;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import com.fix.fepgateway.dataplane.marketdata.delay.DelayedMarketDataAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.web.client.RestClient;

class KisDelayedMarketDataBootstrapTest {

  @Test
  void shouldStartDelayedAndLiveInputSubscriptionsWhenDelayedModeEnabled() throws Exception {
    RecordingKisLiveMarketDataAdapter liveAdapter = new RecordingKisLiveMarketDataAdapter();
    RecordingDelayedMarketDataAdapter delayedAdapter = new RecordingDelayedMarketDataAdapter();
    KisDelayedMarketDataBootstrap bootstrap = new KisDelayedMarketDataBootstrap(delayedProperties(), liveAdapter, delayedAdapter);

    bootstrap.run(new DefaultApplicationArguments(new String[0]));

    assertThat(delayedAdapter.startedSubscriptions()).extracting(MarketDataSubscriptionSpec::subscriptionId)
        .containsExactly("kis-delayed-bootstrap-005930", "kis-delayed-bootstrap-000660");
    assertThat(liveAdapter.startedSubscriptions()).extracting(MarketDataSubscriptionSpec::subscriptionId)
        .containsExactly("kis-live-delay-input-005930", "kis-live-delay-input-000660");
    assertThat(delayedAdapter.startedSubscriptions()).extracting(MarketDataSubscriptionSpec::sourceMode)
        .containsOnly(FepQuoteSourceMode.DELAYED);
    assertThat(liveAdapter.startedSubscriptions()).extracting(MarketDataSubscriptionSpec::sourceMode)
        .containsOnly(FepQuoteSourceMode.LIVE);
  }

  @Test
  void shouldWireLiveInputIntoDelayedAdapter() throws Exception {
    RecordingKisLiveMarketDataAdapter liveAdapter = new RecordingKisLiveMarketDataAdapter();
    RecordingDelayedMarketDataAdapter delayedAdapter = new RecordingDelayedMarketDataAdapter();
    KisDelayedMarketDataBootstrap bootstrap = new KisDelayedMarketDataBootstrap(delayedProperties(), liveAdapter, delayedAdapter);

    bootstrap.run(new DefaultApplicationArguments(new String[0]));
    liveAdapter.emitToFirstSink(new NormalizedQuoteEvent(
        "KIS",
        "005930",
        FepQuoteSourceMode.LIVE,
        Instant.parse("2026-03-19T00:00:10Z"),
        70000L,
        70200L,
        70100L,
        7L,
        false
    ));

    assertThat(delayedAdapter.acceptedEvents()).hasSize(1);
    assertThat(delayedAdapter.acceptedEvents().get(0).symbol()).isEqualTo("005930");
  }

  private FepMarketDataProperties delayedProperties() {
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("KIS");
    properties.setSourceMode("DELAYED");
    properties.getDelayed().setDelayMs(300_000L);
    properties.getDelayed().setDrainIntervalMs(1_000L);
    properties.getKis().setEnv("paper");
    properties.getKis().getWs().setTrId("H0STCNT0");
    properties.getKis().getWs().setCusttype("P");
    properties.getKis().getWs().setSymbols(List.of("005930", "000660"));
    return properties;
  }

  private static final class RecordingKisLiveMarketDataAdapter extends KisLiveMarketDataAdapter {

    private final List<MarketDataSubscriptionSpec> startedSubscriptions = new ArrayList<>();
    private final List<MarketDataEventSink> sinks = new ArrayList<>();

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
      sinks.add(eventSink);
    }

    @Override
    public synchronized void stop(String subscriptionId) {
    }

    private List<MarketDataSubscriptionSpec> startedSubscriptions() {
      return startedSubscriptions;
    }

    private void emitToFirstSink(NormalizedQuoteEvent event) {
      sinks.getFirst().accept(event);
    }
  }

  private static final class RecordingDelayedMarketDataAdapter extends DelayedMarketDataAdapter {

    private final List<MarketDataSubscriptionSpec> startedSubscriptions = new ArrayList<>();
    private final List<NormalizedQuoteEvent> acceptedEvents = new ArrayList<>();

    private RecordingDelayedMarketDataAdapter() {
      super(new FepMarketDataProperties(), new NoOpPersistencePort());
    }

    @Override
    public synchronized void start(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink) {
      startedSubscriptions.add(subscriptionSpec);
    }

    @Override
    public synchronized void stop(String subscriptionId) {
    }

    @Override
    public void acceptLiveEvent(NormalizedQuoteEvent liveEvent) {
      acceptedEvents.add(liveEvent);
    }

    private List<MarketDataSubscriptionSpec> startedSubscriptions() {
      return startedSubscriptions;
    }

    private List<NormalizedQuoteEvent> acceptedEvents() {
      return acceptedEvents;
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
