package com.fix.fepgateway.dataplane.marketdata.kis;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

@Component
public class KisLiveMarketDataBootstrap implements ApplicationRunner, DisposableBean {

  private static final String SUBSCRIPTION_ID_PREFIX = "kis-live-bootstrap-";

  private final FepMarketDataProperties properties;
  private final KisLiveMarketDataAdapter liveMarketDataAdapter;
  private final List<String> bootstrappedSubscriptionIds = new ArrayList<>();

  public KisLiveMarketDataBootstrap(
      FepMarketDataProperties properties,
      KisLiveMarketDataAdapter liveMarketDataAdapter
  ) {
    this.properties = properties;
    this.liveMarketDataAdapter = liveMarketDataAdapter;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.isKisLiveModeEnabled()) {
      return;
    }

    bootstrappedSubscriptionIds.clear();

    for (String symbol : new LinkedHashSet<>(properties.getKis().getWs().getSymbols())) {
      MarketDataSubscriptionSpec subscriptionSpec = new MarketDataSubscriptionSpec(
          SUBSCRIPTION_ID_PREFIX + symbol,
          "KIS",
          symbol,
          FepQuoteSourceMode.LIVE,
          properties.getKis().getWs().getTrId(),
          symbol
      );
      liveMarketDataAdapter.start(subscriptionSpec, event -> {
      });
      bootstrappedSubscriptionIds.add(subscriptionSpec.subscriptionId());
    }
  }

  public void stop() {
    for (String subscriptionId : List.copyOf(bootstrappedSubscriptionIds)) {
      liveMarketDataAdapter.stop(subscriptionId);
    }
    bootstrappedSubscriptionIds.clear();
  }

  @Override
  public void destroy() {
    stop();
  }
}
