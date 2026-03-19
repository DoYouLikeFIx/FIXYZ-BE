package com.fix.fepgateway.dataplane.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.entity.MarketDataSubscription;
import com.fix.fepgateway.repository.MarketDataSubscriptionRepository;
import com.fix.fepgateway.repository.QuoteSnapshotRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LiveMarketDataPersistenceServiceTest {

  @Autowired
  private LiveMarketDataPersistencePort liveMarketDataPersistencePort;

  @Autowired
  private MarketDataSubscriptionRepository marketDataSubscriptionRepository;

  @Autowired
  private QuoteSnapshotRepository quoteSnapshotRepository;

  @AfterEach
  void cleanUp() {
    quoteSnapshotRepository.deleteAll();
    marketDataSubscriptionRepository.deleteAll();
  }

  @Test
  void shouldPersistLiveSnapshotAndAdvanceSubscriptionProgress() {
    MarketDataSubscriptionSpec subscriptionSpec = liveSubscription("bootstrap-005930", "005930");
    NormalizedQuoteEvent event = liveEvent("005930", 17L, Instant.parse("2026-03-19T00:30:01Z"));

    liveMarketDataPersistencePort.activateSubscription(subscriptionSpec);
    liveMarketDataPersistencePort.persistSnapshot(subscriptionSpec, event);

    assertThat(quoteSnapshotRepository.findAll()).hasSize(1);
    MarketDataSubscription subscription = marketDataSubscriptionRepository.findByProviderAndSymbolAndSourceMode(
        "KIS",
        "005930",
        FepQuoteSourceMode.LIVE
    ).orElseThrow();
    assertThat(subscription.getSubscriptionId()).isEqualTo("bootstrap-005930");
    assertThat(subscription.getLastEventOffset()).isEqualTo(17L);
    assertThat(subscription.getLastQuoteAsOf()).isEqualTo(Instant.parse("2026-03-19T00:30:01Z"));
    assertThat(subscription.isActive()).isTrue();
  }

  @Test
  void shouldSkipDuplicateQuoteSnapshotIdAndKeepSingleRow() {
    MarketDataSubscriptionSpec subscriptionSpec = liveSubscription("bootstrap-005930", "005930");
    NormalizedQuoteEvent event = liveEvent("005930", 17L, Instant.parse("2026-03-19T00:30:01Z"));

    liveMarketDataPersistencePort.persistSnapshot(subscriptionSpec, event);
    liveMarketDataPersistencePort.persistSnapshot(subscriptionSpec, event);

    assertThat(quoteSnapshotRepository.findAll()).hasSize(1);
  }

  @Test
  void shouldDeactivateExistingSubscription() {
    MarketDataSubscriptionSpec subscriptionSpec = liveSubscription("bootstrap-005930", "005930");

    liveMarketDataPersistencePort.activateSubscription(subscriptionSpec);
    liveMarketDataPersistencePort.deactivateSubscription(subscriptionSpec);

    MarketDataSubscription subscription = marketDataSubscriptionRepository.findByProviderAndSymbolAndSourceMode(
        "KIS",
        "005930",
        FepQuoteSourceMode.LIVE
    ).orElseThrow();
    assertThat(subscription.isActive()).isFalse();
  }

  private MarketDataSubscriptionSpec liveSubscription(String subscriptionId, String symbol) {
    return new MarketDataSubscriptionSpec(
        subscriptionId,
        "KIS",
        symbol,
        FepQuoteSourceMode.LIVE,
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
        70100L,
        70050L,
        streamOffset,
        false
    );
  }
}
