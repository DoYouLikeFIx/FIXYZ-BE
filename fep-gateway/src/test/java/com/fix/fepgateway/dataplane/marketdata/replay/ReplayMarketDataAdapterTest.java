package com.fix.fepgateway.dataplane.marketdata.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataMetrics;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import com.fix.fepgateway.dataplane.marketdata.QuoteSnapshotFactory;
import com.fix.fepgateway.dataplane.marketdata.QuoteSnapshotIdGenerator;
import com.fix.fepgateway.dataplane.marketdata.ReplayCursorSpec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ReplayMarketDataAdapterTest {

  @Test
  void shouldPersistReplaySnapshotAndAdvanceCursor() {
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    FakeReplayCursorPersistencePort cursorPersistencePort = new FakeReplayCursorPersistencePort();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ReplayMarketDataAdapter adapter = new ReplayMarketDataAdapter(
        replayProperties(),
        persistencePort,
        cursorPersistencePort,
        new ReplayQuoteEventGenerator(),
        new MarketDataMetrics(meterRegistry)
    );
    AtomicReference<NormalizedQuoteEvent> sinkEvent = new AtomicReference<>();

    adapter.start(replaySubscription("replay-sub-005930", "005930"), sinkEvent::set);
    adapter.drainReplayEvents();

    assertThat(persistencePort.activatedSubscriptions()).hasSize(1);
    assertThat(persistencePort.persistedEvents()).hasSize(1);
    assertThat(persistencePort.persistedEvents().get(0).sourceMode()).isEqualTo(FepQuoteSourceMode.REPLAY);
    assertThat(cursorPersistencePort.findByReplayId(cursorPersistencePort.lastReplayId()).orElseThrow().cursorOffset())
        .isEqualTo(1L);
    assertThat(sinkEvent.get()).isNotNull();
    assertThat(meterRegistry.get("fep.marketdata.replay.active.subscriptions").gauge().value()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("fep.marketdata.replay.active.streams").gauge().value()).isEqualTo(1.0d);
  }

  @Test
  void shouldHonorFractionalSpeedFactorDeterministically() {
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    FakeReplayCursorPersistencePort cursorPersistencePort = new FakeReplayCursorPersistencePort();
    FepMarketDataProperties properties = replayProperties();
    properties.getReplay().setSpeedFactor(new BigDecimal("1.5000"));
    ReplayMarketDataAdapter adapter = new ReplayMarketDataAdapter(
        properties,
        persistencePort,
        cursorPersistencePort,
        new ReplayQuoteEventGenerator()
    );

    adapter.start(replaySubscription("replay-sub-005930", "005930"), event -> {
    });
    adapter.drainReplayEvents();
    adapter.drainReplayEvents();

    assertThat(persistencePort.persistedEvents()).hasSize(3);
  }

  @Test
  void shouldProduceDeterministicSnapshotIdsForSameReplaySequence() {
    List<String> first = emittedSnapshotIds();
    List<String> second = emittedSnapshotIds();

    assertThat(first).containsExactlyElementsOf(second);
  }

  private List<String> emittedSnapshotIds() {
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    ReplayMarketDataAdapter adapter = new ReplayMarketDataAdapter(
        replayProperties(),
        persistencePort,
        new FakeReplayCursorPersistencePort(),
        new ReplayQuoteEventGenerator()
    );
    QuoteSnapshotFactory quoteSnapshotFactory = new QuoteSnapshotFactory(new QuoteSnapshotIdGenerator());

    adapter.start(replaySubscription("replay-sub-005930", "005930"), event -> {
    });
    adapter.drainReplayEvents();
    adapter.drainReplayEvents();

    return persistencePort.persistedEvents().stream()
        .map(quoteSnapshotFactory::create)
        .map(snapshot -> snapshot.getQuoteSnapshotId())
        .toList();
  }

  private FepMarketDataProperties replayProperties() {
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("REPLAY");
    properties.setSourceMode("REPLAY");
    properties.getReplay().setSeed("seed-1");
    properties.getReplay().setSpeedFactor(new BigDecimal("1.0000"));
    properties.getReplay().setStartOffset(0L);
    properties.getReplay().setDrainIntervalMs(1_000L);
    properties.getReplay().setSymbols(List.of("005930"));
    return properties;
  }

  private MarketDataSubscriptionSpec replaySubscription(String subscriptionId, String symbol) {
    return new MarketDataSubscriptionSpec(
        subscriptionId,
        "REPLAY",
        symbol,
        FepQuoteSourceMode.REPLAY,
        "REPLAY",
        symbol
    );
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

    private List<NormalizedQuoteEvent> persistedEvents() {
      return persistedEvents;
    }
  }

  private static final class FakeReplayCursorPersistencePort implements ReplayCursorPersistencePort {

    private final java.util.Map<String, ReplayCursorSpec> cursors = new java.util.LinkedHashMap<>();
    private String lastReplayId;

    @Override
    public ReplayCursorSpec activate(ReplayCursorSpec replayCursorSpec) {
      ReplayCursorSpec cursor = cursors.computeIfAbsent(replayCursorSpec.replayId(), ignored -> replayCursorSpec);
      lastReplayId = replayCursorSpec.replayId();
      return cursor;
    }

    @Override
    public ReplayCursorSpec advance(String replayId, long nextCursorOffset) {
      ReplayCursorSpec current = cursors.get(replayId);
      ReplayCursorSpec advanced = new ReplayCursorSpec(
          current.replayId(),
          current.seed(),
          current.symbol(),
          nextCursorOffset,
          current.speedFactor()
      );
      cursors.put(replayId, advanced);
      lastReplayId = replayId;
      return advanced;
    }

    @Override
    public void stop(String replayId) {
      lastReplayId = replayId;
    }

    @Override
    public java.util.Optional<ReplayCursorSpec> find(String replayId) {
      return java.util.Optional.ofNullable(cursors.get(replayId));
    }

    private java.util.Optional<ReplayCursorSpec> findByReplayId(String replayId) {
      return find(replayId);
    }

    private String lastReplayId() {
      return lastReplayId;
    }
  }
}
