package com.fix.fepgateway.dataplane.marketdata.delay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import com.fix.fepgateway.dataplane.marketdata.QuoteSnapshotFactory;
import com.fix.fepgateway.dataplane.marketdata.QuoteSnapshotIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DelayedMarketDataAdapterTest {

  @Test
  void shouldEmitDelayedEventOnlyAfterConfiguredDelay() {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-19T00:00:00Z"));
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    DelayedMarketDataAdapter adapter = newAdapter(clock, persistencePort);
    AtomicReference<NormalizedQuoteEvent> sinkEvent = new AtomicReference<>();

    adapter.start(delayedSubscription("delayed-005930", "005930"), sinkEvent::set);
    adapter.acceptLiveEvent(liveEvent("005930", 7L, Instant.parse("2026-03-19T00:00:10Z")));

    assertThat(persistencePort.persistedEvents()).isEmpty();
    assertThat(sinkEvent.get()).isNull();

    clock.setInstant(Instant.parse("2026-03-19T00:05:09Z"));
    adapter.drainAvailableEvents();
    assertThat(persistencePort.persistedEvents()).isEmpty();

    clock.setInstant(Instant.parse("2026-03-19T00:05:10Z"));
    adapter.drainAvailableEvents();

    assertThat(persistencePort.persistedEvents()).hasSize(1);
    assertThat(persistencePort.persistedEvents().get(0).sourceMode()).isEqualTo(FepQuoteSourceMode.DELAYED);
    assertThat(persistencePort.persistedEvents().get(0).quoteAsOf()).isEqualTo(Instant.parse("2026-03-19T00:00:10Z"));
    assertThat(sinkEvent.get()).isNotNull();
    assertThat(sinkEvent.get().lastTrade()).isEqualTo(70100L);
  }

  @Test
  void shouldProduceDeterministicSnapshotIdsForSameInputSequence() {
    List<String> firstRunIds = emitSnapshotIds();
    List<String> secondRunIds = emitSnapshotIds();

    assertThat(firstRunIds).containsExactlyElementsOf(secondRunIds);
  }

  @Test
  void shouldDropPendingEventsWhenSubscriptionStopsBeforeDrain() {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-19T00:00:00Z"));
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    DelayedMarketDataAdapter adapter = newAdapter(clock, persistencePort);

    adapter.start(delayedSubscription("delayed-005930", "005930"), event -> {
    });
    adapter.acceptLiveEvent(liveEvent("005930", 7L, Instant.parse("2026-03-19T00:00:10Z")));
    adapter.stop("delayed-005930");
    clock.setInstant(Instant.parse("2026-03-19T00:05:10Z"));
    adapter.drainAvailableEvents();

    assertThat(persistencePort.persistedEvents()).isEmpty();
    assertThat(persistencePort.deactivatedSubscriptions()).extracting(MarketDataSubscriptionSpec::subscriptionId)
        .containsExactly("delayed-005930");
  }

  private List<String> emitSnapshotIds() {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-19T00:00:00Z"));
    RecordingPersistencePort persistencePort = new RecordingPersistencePort();
    DelayedMarketDataAdapter adapter = newAdapter(clock, persistencePort);
    QuoteSnapshotFactory quoteSnapshotFactory = new QuoteSnapshotFactory(new QuoteSnapshotIdGenerator());

    adapter.start(delayedSubscription("delayed-005930", "005930"), event -> {
    });
    adapter.acceptLiveEvent(liveEvent("005930", 7L, Instant.parse("2026-03-19T00:00:10Z")));
    adapter.acceptLiveEvent(liveEvent("005930", 8L, Instant.parse("2026-03-19T00:00:11Z")));
    clock.setInstant(Instant.parse("2026-03-19T00:05:11Z"));
    adapter.drainAvailableEvents();

    return persistencePort.persistedEvents().stream()
        .map(quoteSnapshotFactory::create)
        .map(snapshot -> snapshot.getQuoteSnapshotId())
        .toList();
  }

  private DelayedMarketDataAdapter newAdapter(MutableClock clock, RecordingPersistencePort persistencePort) {
    return new DelayedMarketDataAdapter(delayedProperties(), persistencePort, clock);
  }

  private FepMarketDataProperties delayedProperties() {
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("KIS");
    properties.setSourceMode("DELAYED");
    properties.getDelayed().setDelayMs(300_000L);
    properties.getDelayed().setDrainIntervalMs(1_000L);
    return properties;
  }

  private MarketDataSubscriptionSpec delayedSubscription(String subscriptionId, String symbol) {
    return new MarketDataSubscriptionSpec(
        subscriptionId,
        "KIS",
        symbol,
        FepQuoteSourceMode.DELAYED,
        "H0STCNT0",
        symbol
    );
  }

  private NormalizedQuoteEvent liveEvent(String symbol, long streamOffset, Instant quoteAsOf) {
    return new NormalizedQuoteEvent(
        "KIS",
        symbol,
        FepQuoteSourceMode.LIVE,
        quoteAsOf,
        70000L,
        70200L,
        70100L,
        streamOffset,
        false
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

    private List<MarketDataSubscriptionSpec> deactivatedSubscriptions() {
      return deactivatedSubscriptions;
    }

    private List<NormalizedQuoteEvent> persistedEvents() {
      return persistedEvents;
    }
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void setInstant(Instant instant) {
      this.instant = instant;
    }
  }
}
