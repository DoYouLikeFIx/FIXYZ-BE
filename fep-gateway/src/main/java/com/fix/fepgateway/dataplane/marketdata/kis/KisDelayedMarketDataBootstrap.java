package com.fix.fepgateway.dataplane.marketdata.kis;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.delay.DelayedMarketDataAdapter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class KisDelayedMarketDataBootstrap implements ApplicationRunner, DisposableBean {

  private static final String DELAYED_SUBSCRIPTION_ID_PREFIX = "kis-delayed-bootstrap-";
  private static final String LIVE_INPUT_SUBSCRIPTION_ID_PREFIX = "kis-live-delay-input-";

  private final FepMarketDataProperties properties;
  private final KisLiveMarketDataAdapter liveMarketDataAdapter;
  private final DelayedMarketDataAdapter delayedMarketDataAdapter;
  private final List<String> delayedSubscriptionIds = new ArrayList<>();
  private final List<String> liveInputSubscriptionIds = new ArrayList<>();

  public KisDelayedMarketDataBootstrap(
      FepMarketDataProperties properties,
      KisLiveMarketDataAdapter liveMarketDataAdapter,
      DelayedMarketDataAdapter delayedMarketDataAdapter
  ) {
    this.properties = properties;
    this.liveMarketDataAdapter = liveMarketDataAdapter;
    this.delayedMarketDataAdapter = delayedMarketDataAdapter;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.isKisDelayedModeEnabled()) {
      return;
    }

    delayedSubscriptionIds.clear();
    liveInputSubscriptionIds.clear();

    for (String symbol : new LinkedHashSet<>(properties.getKis().getWs().getSymbols())) {
      MarketDataSubscriptionSpec delayedSpec = new MarketDataSubscriptionSpec(
          DELAYED_SUBSCRIPTION_ID_PREFIX + symbol,
          "KIS",
          symbol,
          FepQuoteSourceMode.DELAYED,
          properties.getKis().getWs().getTrId(),
          symbol
      );
      delayedMarketDataAdapter.start(delayedSpec, event -> {
      });
      delayedSubscriptionIds.add(delayedSpec.subscriptionId());

      MarketDataSubscriptionSpec liveInputSpec = new MarketDataSubscriptionSpec(
          LIVE_INPUT_SUBSCRIPTION_ID_PREFIX + symbol,
          "KIS",
          symbol,
          FepQuoteSourceMode.LIVE,
          properties.getKis().getWs().getTrId(),
          symbol
      );
      liveMarketDataAdapter.start(liveInputSpec, delayedMarketDataAdapter::acceptLiveEvent);
      liveInputSubscriptionIds.add(liveInputSpec.subscriptionId());
    }
  }

  public void stop() {
    for (String subscriptionId : List.copyOf(liveInputSubscriptionIds)) {
      liveMarketDataAdapter.stop(subscriptionId);
    }
    for (String subscriptionId : List.copyOf(delayedSubscriptionIds)) {
      delayedMarketDataAdapter.stop(subscriptionId);
    }
    liveInputSubscriptionIds.clear();
    delayedSubscriptionIds.clear();
  }

  @Override
  public void destroy() {
    stop();
  }
}
