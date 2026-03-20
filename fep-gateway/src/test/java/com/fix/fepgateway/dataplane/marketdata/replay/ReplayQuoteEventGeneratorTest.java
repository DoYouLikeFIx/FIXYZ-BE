package com.fix.fepgateway.dataplane.marketdata.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.dataplane.marketdata.ReplayCursorSpec;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReplayQuoteEventGeneratorTest {

  private final ReplayQuoteEventGenerator replayQuoteEventGenerator = new ReplayQuoteEventGenerator();

  @Test
  void shouldGenerateDeterministicReplayEventForSameSeedAndOffset() {
    ReplayCursorSpec replayCursorSpec = new ReplayCursorSpec(
        "replay-005930",
        "seed-1",
        "005930",
        7L,
        new BigDecimal("1.0000")
    );

    var first = replayQuoteEventGenerator.generate(replayCursorSpec);
    var second = replayQuoteEventGenerator.generate(replayCursorSpec);

    assertThat(first).isEqualTo(second);
    assertThat(first.sourceMode()).isEqualTo(FepQuoteSourceMode.REPLAY);
    assertThat(first.bestBid()).isLessThan(first.bestAsk());
  }

  @Test
  void shouldChangeReplayEventWhenCursorOffsetChanges() {
    ReplayCursorSpec firstOffset = new ReplayCursorSpec(
        "replay-005930",
        "seed-1",
        "005930",
        7L,
        new BigDecimal("1.0000")
    );
    ReplayCursorSpec nextOffset = new ReplayCursorSpec(
        "replay-005930",
        "seed-1",
        "005930",
        8L,
        new BigDecimal("1.0000")
    );

    var first = replayQuoteEventGenerator.generate(firstOffset);
    var second = replayQuoteEventGenerator.generate(nextOffset);

    assertThat(second.streamOffset()).isEqualTo(8L);
    assertThat(second.quoteAsOf()).isAfter(first.quoteAsOf());
    assertThat(second).isNotEqualTo(first);
  }
}
