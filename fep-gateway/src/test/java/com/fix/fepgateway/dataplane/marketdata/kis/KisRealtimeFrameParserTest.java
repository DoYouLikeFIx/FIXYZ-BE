package com.fix.fepgateway.dataplane.marketdata.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KisRealtimeFrameParserTest {

  private final KisRealtimeFrameParser parser = new KisRealtimeFrameParser();

  @Test
  void shouldParsePlainRealtimeFrame() {
    KisRealtimeFrame frame = parser.parse("0|H0STCNT0|004|005930^093000^70000");

    assertThat(frame.encrypted()).isFalse();
    assertThat(frame.trId()).isEqualTo("H0STCNT0");
    assertThat(frame.recordCount()).isEqualTo(4);
    assertThat(frame.payload()).isEqualTo("005930^093000^70000");
  }

  @Test
  void shouldRejectMalformedFrameWhenSegmentsAreMissing() {
    assertThatThrownBy(() -> parser.parse("0|H0STCNT0|001"))
        .isInstanceOfSatisfying(KisFrameParseException.class, exception ->
            assertThat(exception.getFailureType()).isEqualTo(KisFrameFailureType.MALFORMED_FRAME));
  }

  @Test
  void shouldRejectMalformedFrameWhenEncFlagIsUnknown() {
    assertThatThrownBy(() -> parser.parse("9|H0STCNT0|001|payload"))
        .isInstanceOfSatisfying(KisFrameParseException.class, exception ->
            assertThat(exception.getFailureType()).isEqualTo(KisFrameFailureType.MALFORMED_FRAME));
  }

  @Test
  void shouldRejectMalformedFrameWhenTrIdIsBlank() {
    assertThatThrownBy(() -> parser.parse("0||001|payload"))
        .isInstanceOfSatisfying(KisFrameParseException.class, exception ->
            assertThat(exception.getFailureType()).isEqualTo(KisFrameFailureType.MALFORMED_FRAME));
  }
}
