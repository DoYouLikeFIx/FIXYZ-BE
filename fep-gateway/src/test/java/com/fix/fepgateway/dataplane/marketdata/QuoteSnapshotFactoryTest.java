package com.fix.fepgateway.dataplane.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.entity.QuoteSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuoteSnapshotFactoryTest {

  private final QuoteSnapshotFactory quoteSnapshotFactory = new QuoteSnapshotFactory(new QuoteSnapshotIdGenerator());

  @Test
  void shouldCreateQuoteSnapshotFromNormalizedEvent() {
    NormalizedQuoteEvent event = new NormalizedQuoteEvent(
        "REPLAY",
        "005930",
        FepQuoteSourceMode.REPLAY,
        Instant.parse("2026-03-19T03:15:30Z"),
        71900L,
        72000L,
        71950L,
        7L,
        false
    );

    QuoteSnapshot snapshot = quoteSnapshotFactory.create(event);

    assertThat(snapshot.getQuoteSnapshotId())
        .isEqualTo("qsnap_50e401d3e471d6be83fa42b74f2c7040ca5138a05e9bcd7aa711cfe8125ae571");
    assertThat(snapshot.getSymbol()).isEqualTo("005930");
    assertThat(snapshot.getSourceMode()).isEqualTo(FepQuoteSourceMode.REPLAY);
    assertThat(snapshot.getQuoteAsOf()).isEqualTo(Instant.parse("2026-03-19T03:15:30Z"));
    assertThat(snapshot.getBestBid()).isEqualTo(71900L);
    assertThat(snapshot.getBestAsk()).isEqualTo(72000L);
    assertThat(snapshot.getLastTrade()).isEqualTo(71950L);
    assertThat(snapshot.getStreamOffset()).isEqualTo(7L);
    assertThat(snapshot.isStale()).isFalse();
  }
}
