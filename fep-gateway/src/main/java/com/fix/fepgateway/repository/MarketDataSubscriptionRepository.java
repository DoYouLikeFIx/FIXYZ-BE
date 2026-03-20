package com.fix.fepgateway.repository;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.entity.MarketDataSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketDataSubscriptionRepository extends JpaRepository<MarketDataSubscription, Long> {
  Optional<MarketDataSubscription> findBySubscriptionId(String subscriptionId);

  Optional<MarketDataSubscription> findByProviderAndSymbolAndSourceMode(
      String provider,
      String symbol,
      FepQuoteSourceMode sourceMode
  );

  List<MarketDataSubscription> findAllByActiveTrueOrderByUpdatedAtAsc();
}
