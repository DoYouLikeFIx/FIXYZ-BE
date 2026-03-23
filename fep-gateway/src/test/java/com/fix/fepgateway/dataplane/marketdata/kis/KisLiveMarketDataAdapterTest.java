package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataMetrics;
import com.fix.fepgateway.dataplane.marketdata.MarketDataEventSink;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionProgress;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionProgressPort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import com.fix.fepgateway.dataplane.marketdata.replay.ReplayQuoteEventGenerator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KisLiveMarketDataAdapterTest {

  @Test
  void shouldConnectOnceAndSendSubscribePayload() throws Exception {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient, persistencePort);

    adapter.start(subscription("sub-005930", "005930"), event -> {
    });
    adapter.start(subscription("sub-000660", "000660"), event -> {
    });

    assertThat(sessionClient.connectedUris())
        .containsExactly(URI.create("ws://ops.koreainvestment.com:31000"));
    assertThat(sessionClient.session().sentPayloads()).hasSize(2);
    assertThat(persistencePort.activatedSubscriptions()).hasSize(2);
    assertThat(new ObjectMapper().readTree(sessionClient.session().sentPayloads().get(0))
        .path("body").path("input").path("tr_key").asText()).isEqualTo("005930");
    assertThat(new ObjectMapper().readTree(sessionClient.session().sentPayloads().get(1))
        .path("body").path("input").path("tr_key").asText()).isEqualTo("000660");
  }

  @Test
  void shouldAvoidDuplicateSubscribeForSameRemoteSubscription() {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient, persistencePort);

    adapter.start(subscription("sub-1", "005930"), event -> {
    });
    adapter.start(subscription("sub-2", "005930"), event -> {
    });

    assertThat(sessionClient.session().sentPayloads()).hasSize(1);
    assertThat(persistencePort.activatedSubscriptions()).hasSize(1);
  }

  @Test
  void shouldSendUnsubscribeAndCloseSessionWhenLastSubscriptionStops() throws Exception {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient, persistencePort);

    adapter.start(subscription("sub-005930", "005930"), event -> {
    });
    adapter.stop("sub-005930");

    assertThat(sessionClient.session().sentPayloads()).hasSize(2);
    assertThat(persistencePort.deactivatedSubscriptions()).extracting(MarketDataSubscriptionSpec::symbol)
        .containsExactly("005930");
    assertThat(new ObjectMapper().readTree(sessionClient.session().sentPayloads().get(1))
        .path("header").path("tr_type").asText()).isEqualTo("2");
    assertThat(sessionClient.session().closed()).isTrue();
  }

  @Test
  void shouldCacheDecryptionContextFromSubscribeSuccessMessage() {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient, new RecordingPersistencePort());

    adapter.start(subscription("sub-005930", "005930"), event -> {
    });
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

  @Test
  void shouldPersistRealtimeFrameAndDispatchSink() {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient, persistencePort, meterRegistry);
    AtomicReference<NormalizedQuoteEvent> sinkEvent = new AtomicReference<>();

    adapter.start(subscription("sub-005930", "005930"), sinkEvent::set);
    sessionClient.session().emit("0|H0STCNT0|001|" + recordPayload("005930", "093001", "70100", "70200", "70000", "20260319"));

    assertThat(persistencePort.persistedEvents()).hasSize(1);
    assertThat(persistencePort.persistedEvents().get(0).symbol()).isEqualTo("005930");
    assertThat(persistencePort.persistedEvents().get(0).quoteAsOf()).isEqualTo(Instant.parse("2026-03-19T00:30:01Z"));
    assertThat(sinkEvent.get()).isNotNull();
    assertThat(sinkEvent.get().lastTrade()).isEqualTo(70100L);
  }

  @Test
  void shouldReconnectAndResubscribeActiveRoutesWhenSessionDrops() throws Exception {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    FixedMarketDataSubscriptionProgressPort progressPort = new FixedMarketDataSubscriptionProgressPort();
    progressPort.put("000660", new MarketDataSubscriptionProgress(40L, Instant.parse("2026-03-19T00:30:00Z")));
    progressPort.put("005930", new MarketDataSubscriptionProgress(10L, Instant.parse("2026-03-19T00:31:00Z")));
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient, persistencePort, progressPort, meterRegistry);

    adapter.start(subscription("sub-005930", "005930"), event -> {
    });
    adapter.start(subscription("sub-000660", "000660"), event -> {
    });
    sessionClient.session().disconnectUnexpectedly();

    assertThat(adapter.reconnectIfNecessary()).isTrue();
    assertThat(sessionClient.connectedUris()).hasSize(2);
    assertThat(sessionClient.sessions().get(1).sentPayloads()).hasSize(2);
    assertThat(new ObjectMapper().readTree(sessionClient.sessions().get(1).sentPayloads().get(0))
        .path("body").path("input").path("tr_key").asText()).isEqualTo("000660");
    assertThat(new ObjectMapper().readTree(sessionClient.sessions().get(1).sentPayloads().get(1))
        .path("body").path("input").path("tr_key").asText()).isEqualTo("005930");
    assertThat(persistencePort.persistedEvents()).hasSize(4);
    assertThat(persistencePort.persistedEvents()).extracting(NormalizedQuoteEvent::symbol)
        .containsExactly("000660", "000660", "005930", "005930");
    assertThat(persistencePort.persistedEvents()).extracting(NormalizedQuoteEvent::sourceMode)
        .containsOnly(FepQuoteSourceMode.REPLAY);

    sessionClient.session().emit("0|H0STCNT0|001|" + recordPayload("005930", "093500", "70200", "70300", "70100", "20260319"));

    assertThat(persistencePort.persistedEvents()).hasSize(5);
    assertThat(persistencePort.persistedEvents().get(4).sourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
    assertThat(persistencePort.persistedEvents().get(4).streamOffset()).isEqualTo(43L);
    assertThat(meterRegistry.get("fep.marketdata.reconnect.attempts")
        .tag("provider", "KIS")
        .counter()
        .count()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("fep.marketdata.reconnect.success")
        .tag("provider", "KIS")
        .counter()
        .count()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("fep.marketdata.kis.session.open").gauge().value()).isEqualTo(1.0d);
  }

  @Test
  void shouldReserveGapFillOffsetsBeforeImmediateReconnectedLiveFrames() {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    FixedMarketDataSubscriptionProgressPort progressPort = new FixedMarketDataSubscriptionProgressPort();
    progressPort.put("000660", new MarketDataSubscriptionProgress(40L, Instant.parse("2026-03-19T00:30:00Z")));
    progressPort.put("005930", new MarketDataSubscriptionProgress(10L, Instant.parse("2026-03-19T00:31:00Z")));
    KisLiveMarketDataAdapter adapter = newAdapter(
        sessionClient,
        persistencePort,
        progressPort,
        new SimpleMeterRegistry()
    );

    adapter.start(subscription("sub-005930", "005930"), event -> {
    });
    adapter.start(subscription("sub-000660", "000660"), event -> {
    });
    sessionClient.session().disconnectUnexpectedly();
    sessionClient.onNextSessionCreated(session -> {
      AtomicBoolean emitted = new AtomicBoolean(false);
      session.onSend(payload -> {
        if (emitted.compareAndSet(false, true)) {
          session.emit(
              "0|H0STCNT0|001|" + recordPayload("000660", "093500", "112000", "112100", "111900", "20260319")
          );
        }
      });
    });

    assertThat(adapter.reconnectIfNecessary()).isTrue();

    assertThat(persistencePort.persistedEvents()).hasSize(5);
    assertThat(persistencePort.persistedEvents()).extracting(NormalizedQuoteEvent::sourceMode)
        .containsExactly(
            FepQuoteSourceMode.REPLAY,
            FepQuoteSourceMode.REPLAY,
            FepQuoteSourceMode.REPLAY,
            FepQuoteSourceMode.REPLAY,
            FepQuoteSourceMode.LIVE
        );
    assertThat(persistencePort.persistedEvents()).extracting(NormalizedQuoteEvent::streamOffset)
        .containsExactly(41L, 42L, 11L, 12L, 43L);
  }

  @Test
  void shouldClearSessionWhenCloseFailsDuringDestroy() {
    FakeKisWebSocketSessionClient sessionClient = new FakeKisWebSocketSessionClient();
    KisLiveMarketDataAdapter adapter = newAdapter(sessionClient, new RecordingPersistencePort());

    adapter.start(subscription("sub-005930", "005930"), event -> {
    });
    sessionClient.session().failOnClose(new IllegalStateException("close failed"));

    assertThatNoException().isThrownBy(adapter::destroy);
    assertThat(sessionClient.session().isOpen()).isFalse();
  }

  private KisLiveMarketDataAdapter newAdapter(
      FakeKisWebSocketSessionClient sessionClient,
      LiveMarketDataPersistencePort persistencePort
  ) {
    return newAdapter(
        sessionClient,
        persistencePort,
        (provider, symbol, sourceMode) -> java.util.Optional.empty(),
        new SimpleMeterRegistry()
    );
  }

  private KisLiveMarketDataAdapter newAdapter(
      FakeKisWebSocketSessionClient sessionClient,
      LiveMarketDataPersistencePort persistencePort,
      SimpleMeterRegistry meterRegistry
  ) {
    return newAdapter(
        sessionClient,
        persistencePort,
        (provider, symbol, sourceMode) -> java.util.Optional.empty(),
        meterRegistry
    );
  }

  private KisLiveMarketDataAdapter newAdapter(
      FakeKisWebSocketSessionClient sessionClient,
      LiveMarketDataPersistencePort persistencePort,
      MarketDataSubscriptionProgressPort progressPort,
      SimpleMeterRegistry meterRegistry
  ) {
    return new KisLiveMarketDataAdapter(
        liveProperties(),
        new KisApprovalKeyService(new StubKisApprovalClient("approval-key-001")),
        sessionClient,
        new KisWebSocketPayloadFactory(new ObjectMapper()),
        new KisWebSocketControlMessageParser(new ObjectMapper()),
        new KisDecryptionContextStore(),
        new KisH0stcnt0RecordParser(new KisRealtimeFrameParser(), new KisPayloadDecryptor()),
        new KisH0stcnt0EventMapper(),
        persistencePort,
        progressPort,
        new ReplayQuoteEventGenerator(),
        new MarketDataMetrics(meterRegistry)
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

  private String recordPayload(
      String symbol,
      String tradeHour,
      String lastTrade,
      String bestAsk,
      String bestBid,
      String businessDate
  ) {
    String[] fields = new String[KisH0stcnt0Record.RECORD_FIELD_COUNT];
    Arrays.fill(fields, "");
    fields[0] = symbol;
    fields[1] = tradeHour;
    fields[2] = lastTrade;
    fields[10] = bestAsk;
    fields[11] = bestBid;
    fields[33] = businessDate;
    fields[34] = "2";
    fields[35] = "N";
    fields[45] = "70500";
    return String.join("^", fields);
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
    private final List<FakeKisWebSocketSession> sessions = new ArrayList<>();
    private final AtomicReference<FakeKisWebSocketSession> session = new AtomicReference<>();
    private java.util.function.Consumer<FakeKisWebSocketSession> nextSessionCreatedHook;

    @Override
    public KisWebSocketSession connect(URI uri, java.util.function.Consumer<String> inboundTextHandler) {
      connectedUris.add(uri);
      FakeKisWebSocketSession fakeSession = new FakeKisWebSocketSession(inboundTextHandler);
      if (nextSessionCreatedHook != null) {
        java.util.function.Consumer<FakeKisWebSocketSession> hook = nextSessionCreatedHook;
        nextSessionCreatedHook = null;
        hook.accept(fakeSession);
      }
      sessions.add(fakeSession);
      session.set(fakeSession);
      return fakeSession;
    }

    private List<URI> connectedUris() {
      return connectedUris;
    }

    private FakeKisWebSocketSession session() {
      return session.get();
    }

    private List<FakeKisWebSocketSession> sessions() {
      return sessions;
    }

    private void onNextSessionCreated(java.util.function.Consumer<FakeKisWebSocketSession> hook) {
      this.nextSessionCreatedHook = hook;
    }
  }

  private static final class FakeKisWebSocketSession implements KisWebSocketSession {

    private final java.util.function.Consumer<String> inboundTextHandler;
    private final List<String> sentPayloads = new ArrayList<>();
    private boolean open = true;
    private boolean closed;
    private RuntimeException closeFailure;
    private java.util.function.Consumer<String> sendHook;

    private FakeKisWebSocketSession(java.util.function.Consumer<String> inboundTextHandler) {
      this.inboundTextHandler = inboundTextHandler;
    }

    @Override
    public void sendText(String payload) {
      sentPayloads.add(payload);
      if (sendHook != null) {
        sendHook.accept(payload);
      }
    }

    @Override
    public void close() {
      open = false;
      closed = true;
      if (closeFailure != null) {
        throw closeFailure;
      }
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

    private void disconnectUnexpectedly() {
      open = false;
    }

    private void failOnClose(RuntimeException closeFailure) {
      this.closeFailure = closeFailure;
    }

    private void onSend(java.util.function.Consumer<String> sendHook) {
      this.sendHook = sendHook;
    }
  }

  private static final class RecordingPersistencePort implements LiveMarketDataPersistencePort {

    private final List<MarketDataSubscriptionSpec> activatedSubscriptions = new ArrayList<>();
    private final List<MarketDataSubscriptionSpec> deactivatedSubscriptions = new ArrayList<>();
    private final List<NormalizedQuoteEvent> persistedEvents = new ArrayList<>();

    @Override
    public void activateSubscription(MarketDataSubscriptionSpec subscriptionSpec) {
      activatedSubscriptions.add(subscriptionSpec);
    }

    @Override
    public void deactivateSubscription(MarketDataSubscriptionSpec subscriptionSpec) {
      deactivatedSubscriptions.add(subscriptionSpec);
    }

    @Override
    public void persistSnapshot(MarketDataSubscriptionSpec subscriptionSpec, NormalizedQuoteEvent event) {
      persistedEvents.add(event);
    }

    private List<MarketDataSubscriptionSpec> activatedSubscriptions() {
      return activatedSubscriptions;
    }

    private List<MarketDataSubscriptionSpec> deactivatedSubscriptions() {
      return deactivatedSubscriptions;
    }

    private List<NormalizedQuoteEvent> persistedEvents() {
      return persistedEvents;
    }
  }

  private static final class FixedMarketDataSubscriptionProgressPort implements MarketDataSubscriptionProgressPort {

    private final Map<String, MarketDataSubscriptionProgress> progressBySymbol = new LinkedHashMap<>();

    @Override
    public java.util.Optional<MarketDataSubscriptionProgress> findProgress(String provider, String symbol, FepQuoteSourceMode sourceMode) {
      return java.util.Optional.ofNullable(progressBySymbol.get(symbol));
    }

    private void put(String symbol, MarketDataSubscriptionProgress progress) {
      progressBySymbol.put(symbol, progress);
    }
  }
}
