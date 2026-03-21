package com.fix.fepgateway.dataplane.marketdata;

import com.fix.common.fep.FepQuoteSourceMode;
import java.util.Optional;

@FunctionalInterface
public interface MarketDataSubscriptionProgressPort {

  Optional<MarketDataSubscriptionProgress> findProgress(String provider, String symbol, FepQuoteSourceMode sourceMode);
}
