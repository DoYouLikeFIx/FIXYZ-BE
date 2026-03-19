package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.fep.FepQuoteSourceMode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class KisH0stcnt0EventMapperTest {

  private final KisH0stcnt0EventMapper eventMapper = new KisH0stcnt0EventMapper();

  @Test
  void shouldConvertKisRecordToNormalizedLiveEvent() {
    var event = eventMapper.toLiveEvent(record("005930", "093001", "70100", "70200", "70000", "20260319"), 11L);

    assertThat(event.provider()).isEqualTo("KIS");
    assertThat(event.symbol()).isEqualTo("005930");
    assertThat(event.sourceMode()).isEqualTo(FepQuoteSourceMode.LIVE);
    assertThat(event.quoteAsOf()).isEqualTo(java.time.Instant.parse("2026-03-19T00:30:01Z"));
    assertThat(event.bestAsk()).isEqualTo(70200L);
    assertThat(event.bestBid()).isEqualTo(70000L);
    assertThat(event.lastTrade()).isEqualTo(70100L);
    assertThat(event.streamOffset()).isEqualTo(11L);
    assertThat(event.stale()).isFalse();
  }

  @Test
  void shouldRejectCrossedQuotes() {
    assertThatThrownBy(() -> eventMapper.toLiveEvent(
        record("005930", "093001", "70100", "70000", "70200", "20260319"),
        11L
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bestBid");
  }

  private KisH0stcnt0Record record(
      String symbol,
      String tradeHour,
      String lastTrade,
      String bestAsk,
      String bestBid,
      String businessDate
  ) {
    String[] fields = new String[KisH0stcnt0Record.RECORD_FIELD_COUNT];
    Arrays.fill(fields, "");
    fields[0] = symbol;
    fields[1] = tradeHour;
    fields[2] = lastTrade;
    fields[10] = bestAsk;
    fields[11] = bestBid;
    fields[33] = businessDate;
    fields[34] = "2";
    fields[35] = "N";
    fields[45] = "70500";
    return new KisH0stcnt0Record(Arrays.asList(fields));
  }
}
