package com.fix.fepgateway.dataplane.marketdata;

@FunctionalInterface
public interface MarketDataEventSink {
  void accept(NormalizedQuoteEvent event);
}
