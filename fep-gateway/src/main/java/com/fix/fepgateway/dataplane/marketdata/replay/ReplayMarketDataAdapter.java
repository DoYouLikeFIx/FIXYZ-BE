package com.fix.fepgateway.dataplane.marketdata.replay;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataEventSink;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSourceAdapter;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
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

  private final Map<String, ActiveReplaySubscription> activeSubscriptions = new ConcurrentHashMap<>();
  private final Map<String, ReplayStreamState> replayStreams = new ConcurrentHashMap<>();
  private final Map<String, Integer> replayStreamRefCounts = new ConcurrentHashMap<>();

  @Autowired
  public ReplayMarketDataAdapter(
      FepMarketDataProperties properties,
      LiveMarketDataPersistencePort marketDataPersistencePort,
      ReplayCursorPersistencePort replayCursorPersistencePort,
      ReplayQuoteEventGenerator replayQuoteEventGenerator
  ) {
    this.properties = properties;
    this.marketDataPersistencePort = marketDataPersistencePort;
    this.replayCursorPersistencePort = replayCursorPersistencePort;
    this.replayQuoteEventGenerator = replayQuoteEventGenerator;
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
    validateSubscriptionSpec(subscriptionSpec, eventSink);
    if (activeSubscriptions.containsKey(subscriptionSpec.subscriptionId())) {
      return;
    }

    String replayId = replayIdFor(subscriptionSpec.symbol());
    ActiveReplaySubscription activeReplaySubscription = new ActiveReplaySubscription(subscriptionSpec, eventSink, replayId);
    boolean requiresActivation = !replayStreams.containsKey(replayId);

    activeSubscriptions.put(subscriptionSpec.subscriptionId(), activeReplaySubscription);
    replayStreamRefCounts.merge(replayId, 1, Integer::sum);

    if (requiresActivation) {
      ReplayCursorSpec activatedCursor = replayCursorPersistencePort.activate(defaultCursorSpec(subscriptionSpec.symbol()));
      replayStreams.put(replayId, new ReplayStreamState(subscriptionSpec, activatedCursor));
      marketDataPersistencePort.activateSubscription(subscriptionSpec);
    }
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
  }

  @Scheduled(fixedDelayString = "${fep.marketdata.replay.drain-interval-ms:1000}")
  void scheduledDrain() {
    if (!properties.isReplayModeEnabled()) {
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
  }

  private void emitNextReplayEvent(String replayId, MarketDataSubscriptionSpec representativeSpec) {
    ReplayStreamState streamState = replayStreams.get(replayId);
    if (streamState == null) {
      return;
    }

    try {
      ReplayCursorSpec currentCursor = streamState.cursorSpec();
      NormalizedQuoteEvent event = replayQuoteEventGenerator.generate(currentCursor);
      marketDataPersistencePort.persistSnapshot(representativeSpec, event);
      dispatchToSinks(replayId, event);
      ReplayCursorSpec advancedCursor = replayCursorPersistencePort.advance(replayId, currentCursor.cursorOffset() + 1);
      synchronized (this) {
        ReplayStreamState state = replayStreams.get(replayId);
        if (state != null) {
          state.cursorSpec = advancedCursor;
        }
      }
    } catch (RuntimeException exception) {
      log.warn("Failed to emit replay market-data event. replayId={}", replayId, exception);
    }
  }

  private void dispatchToSinks(String replayId, NormalizedQuoteEvent event) {
    activeSubscriptions.values().stream()
        .filter(activeReplaySubscription -> replayId.equals(activeReplaySubscription.replayId()))
        .forEach(activeReplaySubscription -> {
          try {
            activeReplaySubscription.eventSink().accept(event);
          } catch (RuntimeException exception) {
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
        replayIdFor(symbol),
        properties.getReplay().getSeed(),
        symbol,
        properties.getReplay().getStartOffset(),
        properties.getReplay().getSpeedFactor()
    );
  }

  private String replayIdFor(String symbol) {
    return UUID.nameUUIDFromBytes((properties.getReplay().getSeed() + "|" + symbol).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private int decrementReplayStreamRefCount(String replayId) {
    Integer remaining = replayStreamRefCounts.computeIfPresent(
        replayId,
        (key, refCount) -> refCount > 1 ? refCount - 1 : null
    );
    return remaining == null ? 0 : remaining;
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

  private static final class ReplayStreamState {

    private MarketDataSubscriptionSpec representativeSpec;
    private ReplayCursorSpec cursorSpec;
    private BigDecimal emissionCredit = BigDecimal.ZERO;

    private ReplayStreamState(MarketDataSubscriptionSpec representativeSpec, ReplayCursorSpec cursorSpec) {
      this.representativeSpec = representativeSpec;
      this.cursorSpec = cursorSpec;
    }

    private void accrueCredit() {
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
