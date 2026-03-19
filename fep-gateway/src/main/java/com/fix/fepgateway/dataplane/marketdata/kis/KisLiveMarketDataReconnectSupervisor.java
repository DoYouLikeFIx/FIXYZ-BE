package com.fix.fepgateway.dataplane.marketdata.kis;

import com.fix.fepgateway.config.FepMarketDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KisLiveMarketDataReconnectSupervisor {

  private static final Logger log = LoggerFactory.getLogger(KisLiveMarketDataReconnectSupervisor.class);

  private final FepMarketDataProperties properties;
  private final KisLiveMarketDataAdapter kisLiveMarketDataAdapter;

  public KisLiveMarketDataReconnectSupervisor(
      FepMarketDataProperties properties,
      KisLiveMarketDataAdapter kisLiveMarketDataAdapter
  ) {
    this.properties = properties;
    this.kisLiveMarketDataAdapter = kisLiveMarketDataAdapter;
  }

  @Scheduled(fixedDelayString = "${fep.marketdata.kis.ws.supervisor-interval-ms:5000}")
  void supervise() {
    if (!properties.isKisStreamingModeEnabled()) {
      return;
    }

    try {
      if (kisLiveMarketDataAdapter.reconnectIfNecessary()) {
        log.info("Recovered KIS websocket session and resubscribed active market-data routes");
      }
    } catch (RuntimeException exception) {
      log.warn("Failed to recover KIS websocket session", exception);
    }
  }
}
