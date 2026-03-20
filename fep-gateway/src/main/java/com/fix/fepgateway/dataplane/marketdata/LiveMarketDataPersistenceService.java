package com.fix.fepgateway.dataplane.marketdata;

import com.fix.fepgateway.entity.MarketDataSubscription;
import com.fix.fepgateway.entity.QuoteSnapshot;
import com.fix.fepgateway.repository.MarketDataSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LiveMarketDataPersistenceService implements LiveMarketDataPersistencePort {

  private final MarketDataSubscriptionRepository marketDataSubscriptionRepository;
  private final QuoteSnapshotJdbcWriter quoteSnapshotJdbcWriter;
  private final QuoteSnapshotFactory quoteSnapshotFactory;
  private final MarketDataMetrics marketDataMetrics;

  @Override
  @Transactional
  public void activateSubscription(MarketDataSubscriptionSpec subscriptionSpec) {
    MarketDataSubscription subscription = findOrCreateSubscription(subscriptionSpec);
    subscription.synchronizeRouting(
        subscriptionSpec.subscriptionId(),
        subscriptionSpec.trId(),
        subscriptionSpec.trKey()
    );
    subscription.activate();
    marketDataSubscriptionRepository.save(subscription);
  }

  @Override
  @Transactional
  public void deactivateSubscription(MarketDataSubscriptionSpec subscriptionSpec) {
    marketDataSubscriptionRepository.findByProviderAndSymbolAndSourceMode(
        subscriptionSpec.provider(),
        subscriptionSpec.symbol(),
        subscriptionSpec.sourceMode()
    ).ifPresent(subscription -> {
      subscription.deactivate();
      marketDataSubscriptionRepository.save(subscription);
    });
  }

  @Override
  @Transactional
  public void persistSnapshot(MarketDataSubscriptionSpec subscriptionSpec, NormalizedQuoteEvent event) {
    QuoteSnapshot snapshot = quoteSnapshotFactory.create(event);
    if (quoteSnapshotJdbcWriter.insertIfAbsent(snapshot)) {
      marketDataMetrics.recordSnapshotPersisted(event);
    }

    MarketDataSubscription subscription = findOrCreateSubscription(subscriptionSpec);
    subscription.synchronizeRouting(
        subscriptionSpec.subscriptionId(),
        subscriptionSpec.trId(),
        subscriptionSpec.trKey()
    );
    subscription.activate();
    subscription.updateProgress(event.streamOffset(), event.quoteAsOf());
    marketDataSubscriptionRepository.save(subscription);
  }

  private MarketDataSubscription findOrCreateSubscription(MarketDataSubscriptionSpec subscriptionSpec) {
    return marketDataSubscriptionRepository.findByProviderAndSymbolAndSourceMode(
        subscriptionSpec.provider(),
        subscriptionSpec.symbol(),
        subscriptionSpec.sourceMode()
    ).orElseGet(() -> MarketDataSubscription.active(
        subscriptionSpec.subscriptionId(),
        subscriptionSpec.provider(),
        subscriptionSpec.symbol(),
        subscriptionSpec.sourceMode(),
        subscriptionSpec.trId(),
        subscriptionSpec.trKey()
    ));
  }
}
