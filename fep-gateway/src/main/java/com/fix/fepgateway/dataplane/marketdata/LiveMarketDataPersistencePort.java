package com.fix.fepgateway.dataplane.marketdata;

public interface LiveMarketDataPersistencePort {

  void activateSubscription(MarketDataSubscriptionSpec subscriptionSpec);

  void deactivateSubscription(MarketDataSubscriptionSpec subscriptionSpec);

  void persistSnapshot(MarketDataSubscriptionSpec subscriptionSpec, NormalizedQuoteEvent event);
}
