package com.fix.fepgateway.dataplane.marketdata;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.repository.QuoteSnapshotRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MarketDataMetrics {

  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final AtomicInteger kisActiveSubscriptions = new AtomicInteger(0);
  private final AtomicInteger kisRemoteSubscriptions = new AtomicInteger(0);
  private final AtomicInteger kisSessionOpen = new AtomicInteger(0);
  private final AtomicInteger replayActiveSubscriptions = new AtomicInteger(0);
  private final AtomicInteger replayActiveStreams = new AtomicInteger(0);
  private final AtomicLong lastSnapshotPersistedEpochSeconds = new AtomicLong(0);

  @Autowired
  public MarketDataMetrics(MeterRegistry meterRegistry, QuoteSnapshotRepository quoteSnapshotRepository) {
    this(meterRegistry, quoteSnapshotRepository, Clock.systemUTC());
  }

  public MarketDataMetrics(MeterRegistry meterRegistry) {
    this(meterRegistry, null, Clock.systemUTC());
  }

  MarketDataMetrics(MeterRegistry meterRegistry, QuoteSnapshotRepository quoteSnapshotRepository, Clock clock) {
    this.meterRegistry = meterRegistry;
    this.clock = clock;
    seedLastSnapshotPersisted(quoteSnapshotRepository);
    registerGauges();
  }

  public static MarketDataMetrics noOp() {
    return new MarketDataMetrics(new SimpleMeterRegistry());
  }

  public void updateKisState(int activeSubscriptionCount, int remoteSubscriptionCount, boolean sessionOpen) {
    kisActiveSubscriptions.set(activeSubscriptionCount);
    kisRemoteSubscriptions.set(remoteSubscriptionCount);
    kisSessionOpen.set(sessionOpen ? 1 : 0);
  }

  public void updateReplayState(int activeSubscriptionCount, int activeStreamCount) {
    replayActiveSubscriptions.set(activeSubscriptionCount);
    replayActiveStreams.set(activeStreamCount);
  }

  public void recordSnapshotPersisted(NormalizedQuoteEvent event) {
    meterRegistry.counter(
        "fep.marketdata.snapshots.persisted",
        "provider",
        event.provider().toUpperCase(Locale.ROOT),
        "source_mode",
        event.sourceMode().name()
    ).increment();
    lastSnapshotPersistedEpochSeconds.accumulateAndGet(clock.instant().getEpochSecond(), Math::max);
  }

  public void recordReconnectAttempt(String provider) {
    meterRegistry.counter(
        "fep.marketdata.reconnect.attempts",
        "provider",
        normalizeProvider(provider)
    ).increment();
  }

  public void recordReconnectSuccess(String provider) {
    meterRegistry.counter(
        "fep.marketdata.reconnect.success",
        "provider",
        normalizeProvider(provider)
    ).increment();
  }

  public void recordReconnectFailure(String provider) {
    meterRegistry.counter(
        "fep.marketdata.reconnect.failure",
        "provider",
        normalizeProvider(provider)
    ).increment();
  }

  public void recordFrameFailure(String provider, String failureType) {
    meterRegistry.counter(
        "fep.marketdata.frame.failures",
        "provider",
        normalizeProvider(provider),
        "failure_type",
        normalizeFailureType(failureType)
    ).increment();
  }

  public void recordDispatchFailure(String provider, FepQuoteSourceMode sourceMode) {
    meterRegistry.counter(
        "fep.marketdata.dispatch.failures",
        "provider",
        normalizeProvider(provider),
        "source_mode",
        sourceMode.name()
    ).increment();
  }

  private void registerGauges() {
    Gauge.builder("fep.marketdata.kis.active.subscriptions", kisActiveSubscriptions, AtomicInteger::get)
        .register(meterRegistry);
    Gauge.builder("fep.marketdata.kis.remote.subscriptions", kisRemoteSubscriptions, AtomicInteger::get)
        .register(meterRegistry);
    Gauge.builder("fep.marketdata.kis.session.open", kisSessionOpen, AtomicInteger::get)
        .register(meterRegistry);
    Gauge.builder("fep.marketdata.replay.active.subscriptions", replayActiveSubscriptions, AtomicInteger::get)
        .register(meterRegistry);
    Gauge.builder("fep.marketdata.replay.active.streams", replayActiveStreams, AtomicInteger::get)
        .register(meterRegistry);
    Gauge.builder(
        "fep.marketdata.snapshots.last.persisted.epoch.seconds",
        lastSnapshotPersistedEpochSeconds,
        AtomicLong::get
    ).register(meterRegistry);
  }

  private String normalizeProvider(String provider) {
    return provider == null ? "UNKNOWN" : provider.toUpperCase(Locale.ROOT);
  }

  private String normalizeFailureType(String failureType) {
    if (failureType == null || failureType.isBlank()) {
      return "unknown";
    }
    return failureType.toLowerCase(Locale.ROOT);
  }

  private void seedLastSnapshotPersisted(QuoteSnapshotRepository quoteSnapshotRepository) {
    if (quoteSnapshotRepository == null) {
      return;
    }
    quoteSnapshotRepository.findTopByOrderByCreatedAtDesc()
        .map(snapshot -> snapshot.getCreatedAt().getEpochSecond())
        .ifPresent(lastSnapshotPersistedEpochSeconds::set);
  }
}
