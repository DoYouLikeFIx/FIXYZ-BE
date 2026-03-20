package com.fix.fepgateway.dataplane.marketdata.replay;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataMetrics;
import com.fix.fepgateway.dataplane.marketdata.MarketDataEventSink;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSourceAdapter;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import com.fix.fepgateway.dataplane.marketdata.QuoteSnapshotIdGenerator;
import com.fix.fepgateway.dataplane.marketdata.ReplayCursorSpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReplayMarketDataAdapter implements MarketDataSourceAdapter, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(ReplayMarketDataAdapter.class);

  private final FepMarketDataProperties properties;
  private final LiveMarketDataPersistencePort marketDataPersistencePort;
  private final ReplayCursorPersistencePort replayCursorPersistencePort;
  private final ReplayQuoteEventGenerator replayQuoteEventGenerator;
  private final MarketDataMetrics marketDataMetrics;
  private final QuoteSnapshotIdGenerator quoteSnapshotIdGenerator;
  private final ReplaySequenceHasher replaySequenceHasher;

  private final Map<String, ActiveReplaySubscription> activeSubscriptions = new ConcurrentHashMap<>();
  private final Map<String, ReplayStreamState> replayStreams = new ConcurrentHashMap<>();
  private final Map<String, Integer> replayStreamRefCounts = new ConcurrentHashMap<>();

  @Autowired
  public ReplayMarketDataAdapter(
      FepMarketDataProperties properties,
      LiveMarketDataPersistencePort marketDataPersistencePort,
      ReplayCursorPersistencePort replayCursorPersistencePort,
      ReplayQuoteEventGenerator replayQuoteEventGenerator,
      MarketDataMetrics marketDataMetrics,
      QuoteSnapshotIdGenerator quoteSnapshotIdGenerator,
      ReplaySequenceHasher replaySequenceHasher
  ) {
    this.properties = properties;
    this.marketDataPersistencePort = marketDataPersistencePort;
    this.replayCursorPersistencePort = replayCursorPersistencePort;
    this.replayQuoteEventGenerator = replayQuoteEventGenerator;
    this.marketDataMetrics = marketDataMetrics;
    this.quoteSnapshotIdGenerator = quoteSnapshotIdGenerator;
    this.replaySequenceHasher = replaySequenceHasher;
    refreshMetrics();
  }

  ReplayMarketDataAdapter(
      FepMarketDataProperties properties,
      LiveMarketDataPersistencePort marketDataPersistencePort,
      ReplayCursorPersistencePort replayCursorPersistencePort,
      ReplayQuoteEventGenerator replayQuoteEventGenerator,
      MarketDataMetrics marketDataMetrics
  ) {
    this(
        properties,
        marketDataPersistencePort,
        replayCursorPersistencePort,
        replayQuoteEventGenerator,
        marketDataMetrics,
        new QuoteSnapshotIdGenerator(),
        new ReplaySequenceHasher()
    );
  }

  ReplayMarketDataAdapter(
      FepMarketDataProperties properties,
      LiveMarketDataPersistencePort marketDataPersistencePort,
      ReplayCursorPersistencePort replayCursorPersistencePort,
      ReplayQuoteEventGenerator replayQuoteEventGenerator
  ) {
    this(
        properties,
        marketDataPersistencePort,
        replayCursorPersistencePort,
        replayQuoteEventGenerator,
        MarketDataMetrics.noOp(),
        new QuoteSnapshotIdGenerator(),
        new ReplaySequenceHasher()
    );
  }

  @Override
  public String provider() {
    return "REPLAY";
  }

  @Override
  public FepQuoteSourceMode sourceMode() {
    return FepQuoteSourceMode.REPLAY;
  }

  @Override
  public synchronized void start(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink) {
    start(subscriptionSpec, eventSink, defaultCursorSpec(subscriptionSpec.symbol()));
  }

  public synchronized ReplayTimelineStatus startTimeline(ReplayCursorSpec replayCursorSpec) {
    MarketDataSubscriptionSpec subscriptionSpec = new MarketDataSubscriptionSpec(
        timelineSubscriptionId(replayCursorSpec.replayId()),
        provider(),
        replayCursorSpec.symbol(),
        sourceMode(),
        "REPLAY",
        replayCursorSpec.symbol()
    );
    boolean requiresActivation = registerActiveSubscription(subscriptionSpec, replayCursorSpec.replayId(), event -> {
    });
    ReplayCursorSpec resetCursor = replayCursorPersistencePort.reset(replayCursorSpec);
    replayStreams.put(replayCursorSpec.replayId(), new ReplayStreamState(subscriptionSpec, resetCursor));
    if (requiresActivation) {
      marketDataPersistencePort.activateSubscription(subscriptionSpec);
    }
    refreshMetrics();
    return getTimelineStatus(replayCursorSpec.replayId());
  }

  public synchronized ReplayTimelineStatus pauseTimeline(String replayId) {
    ReplayStreamState streamState = replayStreams.get(replayId);
    if (streamState == null) {
      return null;
    }

    replayCursorPersistencePort.pause(replayId);
    streamState.pause();
    return getTimelineStatus(replayId);
  }

  public synchronized ReplayTimelineStatus resumeTimeline(String replayId) {
    ReplayStreamState streamState = replayStreams.get(replayId);
    if (streamState == null) {
      return null;
    }

    replayCursorPersistencePort.resume(replayId);
    streamState.resume();
    return getTimelineStatus(replayId);
  }

  public synchronized ReplayTimelineStatus getTimelineStatus(String replayId) {
    ReplayStreamState streamState = replayStreams.get(replayId);
    if (streamState == null) {
      return null;
    }

    return new ReplayTimelineStatus(
        replayId,
        streamState.cursorSpec().symbol(),
        streamState.cursorSpec().seed(),
        streamState.cursorSpec().cursorOffset(),
        streamState.cursorSpec().speedFactor(),
        streamState.status(),
        (long) streamState.emittedSnapshotIds().size(),
        replaySequenceHasher.hashSequence(List.copyOf(streamState.emittedSnapshotIds()))
    );
  }

  private void start(
      MarketDataSubscriptionSpec subscriptionSpec,
      MarketDataEventSink eventSink,
      ReplayCursorSpec initialCursorSpec
  ) {
    validateSubscriptionSpec(subscriptionSpec, eventSink);
    if (activeSubscriptions.containsKey(subscriptionSpec.subscriptionId())) {
      return;
    }

    String replayId = initialCursorSpec.replayId();
    boolean requiresActivation = registerActiveSubscription(subscriptionSpec, replayId, eventSink);

    if (requiresActivation) {
      ReplayCursorSpec activatedCursor = replayCursorPersistencePort.activate(initialCursorSpec);
      replayStreams.put(replayId, new ReplayStreamState(subscriptionSpec, activatedCursor));
      marketDataPersistencePort.activateSubscription(subscriptionSpec);
    }
    refreshMetrics();
  }

  @Override
  public synchronized void stop(String subscriptionId) {
    ActiveReplaySubscription activeReplaySubscription = activeSubscriptions.remove(subscriptionId);
    if (activeReplaySubscription == null) {
      return;
    }

    int remaining = decrementReplayStreamRefCount(activeReplaySubscription.replayId());
    if (remaining == 0) {
      replayStreams.remove(activeReplaySubscription.replayId());
      replayCursorPersistencePort.stop(activeReplaySubscription.replayId());
      marketDataPersistencePort.deactivateSubscription(activeReplaySubscription.spec());
    } else {
      replayStreams.computeIfPresent(activeReplaySubscription.replayId(), (replayId, streamState) -> {
        activeSubscriptions.values().stream()
            .filter(subscription -> replayId.equals(subscription.replayId()))
            .findFirst()
            .ifPresent(subscription -> streamState.representativeSpec = subscription.spec());
        return streamState;
      });
    }
    refreshMetrics();
  }

  @Scheduled(fixedDelayString = "${fep.marketdata.replay.drain-interval-ms:1000}")
  void scheduledDrain() {
    if (!properties.isReplayModeEnabled() && replayStreams.isEmpty()) {
      return;
    }
    drainReplayEvents();
  }

  void drainReplayEvents() {
    List<EmissionBatch> emissionBatches = new ArrayList<>();

    synchronized (this) {
      replayStreams.values().stream()
          .sorted((left, right) -> left.cursorSpec().replayId().compareTo(right.cursorSpec().replayId()))
          .forEach(streamState -> {
            streamState.accrueCredit();
            int emissionCount = streamState.takeEmissionCount();
            if (emissionCount > 0) {
              emissionBatches.add(new EmissionBatch(streamState.representativeSpec(), streamState.cursorSpec().replayId(), emissionCount));
            }
          });
    }

    for (EmissionBatch emissionBatch : emissionBatches) {
      for (int emissionIndex = 0; emissionIndex < emissionBatch.emissionCount(); emissionIndex++) {
        emitNextReplayEvent(emissionBatch.replayId(), emissionBatch.representativeSpec());
      }
    }
  }

  @Override
  public synchronized void destroy() {
    for (String replayId : List.copyOf(replayStreams.keySet())) {
      replayCursorPersistencePort.stop(replayId);
    }
    activeSubscriptions.clear();
    replayStreamRefCounts.clear();
    replayStreams.clear();
    refreshMetrics();
  }

  private void emitNextReplayEvent(String replayId, MarketDataSubscriptionSpec representativeSpec) {
    ReplayStreamState streamState = replayStreams.get(replayId);
    if (streamState == null) {
      return;
    }
    if (replayCursorPersistencePort.find(replayId).isEmpty()) {
      removeOrphanedReplayStream(replayId, representativeSpec);
      return;
    }

    try {
      ReplayCursorSpec currentCursor = streamState.cursorSpec();
      NormalizedQuoteEvent event = replayQuoteEventGenerator.generate(currentCursor);
      String snapshotId = quoteSnapshotIdGenerator.generate(event);
      marketDataPersistencePort.persistSnapshot(representativeSpec, event);
      dispatchToSinks(replayId, event);
      ReplayCursorSpec advancedCursor = replayCursorPersistencePort.advance(replayId, currentCursor.cursorOffset() + 1);
      synchronized (this) {
        ReplayStreamState state = replayStreams.get(replayId);
        if (state != null) {
          state.recordEmission(snapshotId);
          state.cursorSpec = advancedCursor;
        }
      }
    } catch (IllegalStateException exception) {
      removeOrphanedReplayStream(replayId, representativeSpec);
      log.info("Dropped replay market-data timeline after cursor disappeared. replayId={}", replayId);
    } catch (RuntimeException exception) {
      log.warn("Failed to emit replay market-data event. replayId={}", replayId, exception);
    }
  }

  private void removeOrphanedReplayStream(String replayId, MarketDataSubscriptionSpec representativeSpec) {
    synchronized (this) {
      replayStreams.remove(replayId);
      replayStreamRefCounts.remove(replayId);
      activeSubscriptions.entrySet().removeIf(entry -> replayId.equals(entry.getValue().replayId()));
      refreshMetrics();
    }
    marketDataPersistencePort.deactivateSubscription(representativeSpec);
  }

  private void dispatchToSinks(String replayId, NormalizedQuoteEvent event) {
    activeSubscriptions.values().stream()
        .filter(activeReplaySubscription -> replayId.equals(activeReplaySubscription.replayId()))
        .forEach(activeReplaySubscription -> {
          try {
            activeReplaySubscription.eventSink().accept(event);
          } catch (RuntimeException exception) {
            marketDataMetrics.recordDispatchFailure(provider(), sourceMode());
            log.warn(
                "Failed to dispatch replay event to sink. subscriptionId={}",
                activeReplaySubscription.spec().subscriptionId(),
                exception
            );
          }
        });
  }

  private ReplayCursorSpec defaultCursorSpec(String symbol) {
    return new ReplayCursorSpec(
        replayIdFor(properties.getReplay().getSeed(), symbol),
        properties.getReplay().getSeed(),
        symbol,
        properties.getReplay().getStartOffset(),
        properties.getReplay().getSpeedFactor()
    );
  }

  private String replayIdFor(String seed, String symbol) {
    return UUID.nameUUIDFromBytes((seed + "|" + symbol).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private String timelineSubscriptionId(String replayId) {
    return replayId;
  }

  private int decrementReplayStreamRefCount(String replayId) {
    Integer remaining = replayStreamRefCounts.computeIfPresent(
        replayId,
        (key, refCount) -> refCount > 1 ? refCount - 1 : null
    );
    return remaining == null ? 0 : remaining;
  }

  private boolean registerActiveSubscription(
      MarketDataSubscriptionSpec subscriptionSpec,
      String replayId,
      MarketDataEventSink eventSink
  ) {
    if (activeSubscriptions.containsKey(subscriptionSpec.subscriptionId())) {
      return !replayStreams.containsKey(replayId);
    }

    activeSubscriptions.put(
        subscriptionSpec.subscriptionId(),
        new ActiveReplaySubscription(subscriptionSpec, eventSink, replayId)
    );
    Integer previousRefCount = replayStreamRefCounts.putIfAbsent(replayId, 1);
    if (previousRefCount != null) {
      replayStreamRefCounts.put(replayId, previousRefCount + 1);
    }
    return previousRefCount == null;
  }

  private void validateSubscriptionSpec(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink) {
    if (subscriptionSpec == null) {
      throw new IllegalArgumentException("subscriptionSpec must not be null");
    }
    if (eventSink == null) {
      throw new IllegalArgumentException("eventSink must not be null");
    }
    if (!provider().equalsIgnoreCase(subscriptionSpec.provider())) {
      throw new IllegalArgumentException("subscriptionSpec.provider must be REPLAY");
    }
    if (subscriptionSpec.sourceMode() != FepQuoteSourceMode.REPLAY) {
      throw new IllegalArgumentException("subscriptionSpec.sourceMode must be REPLAY");
    }
  }

  private void refreshMetrics() {
    marketDataMetrics.updateReplayState(activeSubscriptions.size(), replayStreams.size());
  }

  private static final class ReplayStreamState {

    private MarketDataSubscriptionSpec representativeSpec;
    private ReplayCursorSpec cursorSpec;
    private BigDecimal emissionCredit = BigDecimal.ZERO;
    private final List<String> emittedSnapshotIds = new ArrayList<>();
    private String status = "RUNNING";

    private ReplayStreamState(MarketDataSubscriptionSpec representativeSpec, ReplayCursorSpec cursorSpec) {
      this.representativeSpec = representativeSpec;
      this.cursorSpec = cursorSpec;
    }

    private void accrueCredit() {
      if (!"RUNNING".equals(status)) {
        return;
      }
      emissionCredit = emissionCredit.add(cursorSpec.speedFactor());
    }

    private int takeEmissionCount() {
      int emissionCount = emissionCredit.setScale(0, RoundingMode.DOWN).intValue();
      emissionCredit = emissionCredit.subtract(BigDecimal.valueOf(emissionCount));
      return emissionCount;
    }

    private ReplayCursorSpec cursorSpec() {
      return cursorSpec;
    }

    private MarketDataSubscriptionSpec representativeSpec() {
      return representativeSpec;
    }

    private List<String> emittedSnapshotIds() {
      return emittedSnapshotIds;
    }

    private void recordEmission(String snapshotId) {
      emittedSnapshotIds.add(snapshotId);
    }

    private String status() {
      return status;
    }

    private void pause() {
      status = "PAUSED";
    }

    private void resume() {
      status = "RUNNING";
    }
  }

  private record ActiveReplaySubscription(
      MarketDataSubscriptionSpec spec,
      MarketDataEventSink eventSink,
      String replayId
  ) {
  }

  private record EmissionBatch(
      MarketDataSubscriptionSpec representativeSpec,
      String replayId,
      int emissionCount
  ) {
  }
}
