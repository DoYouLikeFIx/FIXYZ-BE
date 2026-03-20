package com.fix.fepgateway.dataplane.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepQuoteSourceMode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuoteSnapshotIdGeneratorTest {

  private final QuoteSnapshotIdGenerator quoteSnapshotIdGenerator = new QuoteSnapshotIdGenerator();

  @Test
  void shouldGenerateStableSnapshotIdForSameEvent() {
    NormalizedQuoteEvent event = new NormalizedQuoteEvent(
        "KIS_H0STCNT0",
        "005930",
        FepQuoteSourceMode.LIVE,
        Instant.parse("2026-03-19T03:15:30.123456789Z"),
        72000L,
        72100L,
        72050L,
        42L,
        false
    );

    String first = quoteSnapshotIdGenerator.generate(event);
    String second = quoteSnapshotIdGenerator.generate(event);

    assertThat(first).isEqualTo("qsnap_e6e18f666415166f87fd0e2eb6a56469473c0ad64e80bb9f1c27ab270ad25e0c");
    assertThat(second).isEqualTo(first);
  }

  @Test
  void shouldChangeSnapshotIdWhenStreamOffsetChanges() {
    NormalizedQuoteEvent first = new NormalizedQuoteEvent(
        "KIS_H0STCNT0",
        "005930",
        FepQuoteSourceMode.LIVE,
        Instant.parse("2026-03-19T03:15:30.123456789Z"),
        72000L,
        72100L,
        72050L,
        42L,
        false
    );
    NormalizedQuoteEvent second = new NormalizedQuoteEvent(
        "KIS_H0STCNT0",
        "005930",
        FepQuoteSourceMode.LIVE,
        Instant.parse("2026-03-19T03:15:30.123456789Z"),
        72000L,
        72100L,
        72050L,
        43L,
        false
    );

    assertThat(quoteSnapshotIdGenerator.generate(first))
        .isNotEqualTo(quoteSnapshotIdGenerator.generate(second));
  }
}
