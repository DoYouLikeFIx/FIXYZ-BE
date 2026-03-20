package com.fix.fepgateway.dataplane.marketdata.delay;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataEventSink;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSourceAdapter;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DelayedMarketDataAdapter implements MarketDataSourceAdapter, DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(DelayedMarketDataAdapter.class);

  private final FepMarketDataProperties properties;
  private final LiveMarketDataPersistencePort persistencePort;
  private final Clock clock;

  private final Map<String, ActiveDelayedSubscription> activeSubscriptions = new ConcurrentHashMap<>();
  private final PriorityQueue<QueuedDelayedEvent> pendingEvents = new PriorityQueue<>(
      Comparator.comparing(QueuedDelayedEvent::releaseAt)
          .thenComparingLong(entry -> entry.event().streamOffset())
          .thenComparing(entry -> entry.subscription().spec().subscriptionId())
  );

  @Autowired
  public DelayedMarketDataAdapter(
      FepMarketDataProperties properties,
      LiveMarketDataPersistencePort persistencePort
  ) {
    this(properties, persistencePort, Clock.systemUTC());
  }

  DelayedMarketDataAdapter(
      FepMarketDataProperties properties,
      LiveMarketDataPersistencePort persistencePort,
      Clock clock
  ) {
    this.properties = properties;
    this.persistencePort = persistencePort;
    this.clock = clock;
  }

  @Override
  public String provider() {
    return "KIS";
  }

  @Override
  public FepQuoteSourceMode sourceMode() {
    return FepQuoteSourceMode.DELAYED;
  }

  @Override
  public synchronized void start(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink) {
    validateSubscriptionSpec(subscriptionSpec, eventSink);
    if (activeSubscriptions.containsKey(subscriptionSpec.subscriptionId())) {
      return;
    }
    activeSubscriptions.put(subscriptionSpec.subscriptionId(), new ActiveDelayedSubscription(subscriptionSpec, eventSink));
    persistencePort.activateSubscription(subscriptionSpec);
  }

  @Override
  public synchronized void stop(String subscriptionId) {
    ActiveDelayedSubscription removed = activeSubscriptions.remove(subscriptionId);
    if (removed == null) {
      return;
    }

    synchronized (pendingEvents) {
      pendingEvents.removeIf(entry -> entry.subscription().spec().subscriptionId().equals(subscriptionId));
    }

    boolean hasRemainingForSymbol = activeSubscriptions.values().stream()
        .anyMatch(subscription -> sameRoute(subscription.spec(), removed.spec()));
    if (!hasRemainingForSymbol) {
      persistencePort.deactivateSubscription(removed.spec());
    }
  }

  public void acceptLiveEvent(NormalizedQuoteEvent liveEvent) {
    if (liveEvent == null || liveEvent.sourceMode() != FepQuoteSourceMode.LIVE) {
      return;
    }

    List<ActiveDelayedSubscription> matchingSubscriptions = activeSubscriptions.values().stream()
        .filter(subscription -> subscription.spec().symbol().equals(liveEvent.symbol()))
        .toList();
    if (matchingSubscriptions.isEmpty()) {
      return;
    }

    synchronized (pendingEvents) {
      for (ActiveDelayedSubscription subscription : matchingSubscriptions) {
        pendingEvents.add(new QueuedDelayedEvent(
            subscription,
            project(liveEvent),
            liveEvent.quoteAsOf().plusMillis(properties.getDelayed().getDelayMs())
        ));
      }
    }

    drainAvailableEvents();
  }

  @Scheduled(fixedDelayString = "${fep.marketdata.delayed.drain-interval-ms:1000}")
  void scheduledDrain() {
    if (!properties.isKisDelayedModeEnabled()) {
      return;
    }
    drainAvailableEvents();
  }

  void drainAvailableEvents() {
    List<QueuedDelayedEvent> readyEvents = new ArrayList<>();

    synchronized (pendingEvents) {
      Instant now = clock.instant();
      while (!pendingEvents.isEmpty() && !pendingEvents.peek().releaseAt().isAfter(now)) {
        readyEvents.add(pendingEvents.poll());
      }
    }

    for (QueuedDelayedEvent readyEvent : readyEvents) {
      try {
        persistencePort.persistSnapshot(readyEvent.subscription().spec(), readyEvent.event());
        readyEvent.subscription().eventSink().accept(readyEvent.event());
      } catch (RuntimeException exception) {
        log.warn(
            "Failed to dispatch delayed market-data event. subscriptionId={}",
            readyEvent.subscription().spec().subscriptionId(),
            exception
        );
      }
    }
  }

  @Override
  public synchronized void destroy() {
    activeSubscriptions.clear();
    synchronized (pendingEvents) {
      pendingEvents.clear();
    }
  }

  private NormalizedQuoteEvent project(NormalizedQuoteEvent liveEvent) {
    return new NormalizedQuoteEvent(
        liveEvent.provider(),
        liveEvent.symbol(),
        FepQuoteSourceMode.DELAYED,
        liveEvent.quoteAsOf(),
        liveEvent.bestBid(),
        liveEvent.bestAsk(),
        liveEvent.lastTrade(),
        liveEvent.streamOffset(),
        liveEvent.stale()
    );
  }

  private void validateSubscriptionSpec(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink) {
    if (subscriptionSpec == null) {
      throw new IllegalArgumentException("subscriptionSpec must not be null");
    }
    if (eventSink == null) {
      throw new IllegalArgumentException("eventSink must not be null");
    }
    if (!provider().equalsIgnoreCase(subscriptionSpec.provider())) {
      throw new IllegalArgumentException("subscriptionSpec.provider must be KIS");
    }
    if (subscriptionSpec.sourceMode() != FepQuoteSourceMode.DELAYED) {
      throw new IllegalArgumentException("subscriptionSpec.sourceMode must be DELAYED");
    }
  }

  private boolean sameRoute(MarketDataSubscriptionSpec left, MarketDataSubscriptionSpec right) {
    return left.provider().equalsIgnoreCase(right.provider())
        && left.symbol().equals(right.symbol())
        && left.sourceMode() == right.sourceMode();
  }

  private record ActiveDelayedSubscription(
      MarketDataSubscriptionSpec spec,
      MarketDataEventSink eventSink
  ) {
  }

  private record QueuedDelayedEvent(
      ActiveDelayedSubscription subscription,
      NormalizedQuoteEvent event,
      Instant releaseAt
  ) {
  }
}
