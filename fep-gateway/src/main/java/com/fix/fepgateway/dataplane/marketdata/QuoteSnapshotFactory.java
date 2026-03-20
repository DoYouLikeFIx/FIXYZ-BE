package com.fix.fepgateway.dataplane.marketdata;

import com.fix.fepgateway.entity.QuoteSnapshot;
import org.springframework.stereotype.Component;

@Component
public class QuoteSnapshotFactory {

  private final QuoteSnapshotIdGenerator quoteSnapshotIdGenerator;

  public QuoteSnapshotFactory(QuoteSnapshotIdGenerator quoteSnapshotIdGenerator) {
    this.quoteSnapshotIdGenerator = quoteSnapshotIdGenerator;
  }

  public QuoteSnapshot create(NormalizedQuoteEvent event) {
    return QuoteSnapshot.recorded(
        quoteSnapshotIdGenerator.generate(event),
        event.symbol(),
        event.sourceMode(),
        event.quoteAsOf(),
        event.bestBid(),
        event.bestAsk(),
        event.lastTrade(),
        event.streamOffset(),
        event.stale()
    );
  }
}
