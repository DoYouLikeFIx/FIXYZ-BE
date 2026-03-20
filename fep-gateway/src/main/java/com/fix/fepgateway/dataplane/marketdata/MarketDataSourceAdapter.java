package com.fix.fepgateway.dataplane.marketdata;

import com.fix.common.fep.FepQuoteSourceMode;

public interface MarketDataSourceAdapter {

  String provider();

  FepQuoteSourceMode sourceMode();

  void start(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink);

  void stop(String subscriptionId);
}
