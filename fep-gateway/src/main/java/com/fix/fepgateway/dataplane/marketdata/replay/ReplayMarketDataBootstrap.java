package com.fix.fepgateway.dataplane.marketdata.replay;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ReplayMarketDataBootstrap implements ApplicationRunner, DisposableBean {

  private static final String SUBSCRIPTION_ID_PREFIX = "replay-bootstrap-";

  private final FepMarketDataProperties properties;
  private final ReplayMarketDataAdapter replayMarketDataAdapter;
  private final List<String> bootstrappedSubscriptionIds = new ArrayList<>();

  public ReplayMarketDataBootstrap(
      FepMarketDataProperties properties,
      ReplayMarketDataAdapter replayMarketDataAdapter
  ) {
    this.properties = properties;
    this.replayMarketDataAdapter = replayMarketDataAdapter;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.isReplayModeEnabled()) {
      return;
    }

    bootstrappedSubscriptionIds.clear();
    for (String symbol : new LinkedHashSet<>(properties.getReplay().getSymbols())) {
      MarketDataSubscriptionSpec subscriptionSpec = new MarketDataSubscriptionSpec(
          SUBSCRIPTION_ID_PREFIX + symbol,
          "REPLAY",
          symbol,
          FepQuoteSourceMode.REPLAY,
          "REPLAY",
          symbol
      );
      replayMarketDataAdapter.start(subscriptionSpec, event -> {
      });
      bootstrappedSubscriptionIds.add(subscriptionSpec.subscriptionId());
    }
  }

  public void stop() {
    for (String subscriptionId : List.copyOf(bootstrappedSubscriptionIds)) {
      replayMarketDataAdapter.stop(subscriptionId);
    }
    bootstrappedSubscriptionIds.clear();
  }

  @Override
  public void destroy() {
    stop();
  }
}
