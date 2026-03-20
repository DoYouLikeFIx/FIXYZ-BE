package com.fix.fepgateway.dataplane.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepQuoteSourceMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MarketDataMetricsTest {

  @Test
  void shouldExposeGaugesAndCountersForMarketDataPipelines() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MarketDataMetrics metrics = new MarketDataMetrics(meterRegistry);

    metrics.updateKisState(2, 1, true);
    metrics.updateReplayState(3, 2);
    metrics.recordReconnectAttempt("KIS");
    metrics.recordReconnectSuccess("KIS");
    metrics.recordReconnectFailure("KIS");
    metrics.recordFrameFailure("KIS", "MALFORMED_FRAME");
    metrics.recordDispatchFailure("REPLAY", FepQuoteSourceMode.REPLAY);
    metrics.recordSnapshotPersisted(new NormalizedQuoteEvent(
        "REPLAY",
        "005930",
        FepQuoteSourceMode.REPLAY,
        Instant.parse("2026-03-19T00:30:01Z"),
        70000L,
        70100L,
        70050L,
        11L,
        false
    ));

    assertThat(meterRegistry.get("fep.marketdata.kis.active.subscriptions").gauge().value()).isEqualTo(2.0d);
    assertThat(meterRegistry.get("fep.marketdata.kis.remote.subscriptions").gauge().value()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("fep.marketdata.kis.session.open").gauge().value()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("fep.marketdata.replay.active.subscriptions").gauge().value()).isEqualTo(3.0d);
    assertThat(meterRegistry.get("fep.marketdata.replay.active.streams").gauge().value()).isEqualTo(2.0d);
    assertThat(meterRegistry.get("fep.marketdata.reconnect.attempts").tag("provider", "KIS").counter().count())
        .isEqualTo(1.0d);
    assertThat(meterRegistry.get("fep.marketdata.reconnect.success").tag("provider", "KIS").counter().count())
        .isEqualTo(1.0d);
    assertThat(meterRegistry.get("fep.marketdata.reconnect.failure").tag("provider", "KIS").counter().count())
        .isEqualTo(1.0d);
    assertThat(meterRegistry.get("fep.marketdata.frame.failures")
        .tag("provider", "KIS")
        .tag("failure_type", "malformed_frame")
        .counter()
        .count()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("fep.marketdata.dispatch.failures")
        .tag("provider", "REPLAY")
        .tag("source_mode", "REPLAY")
        .counter()
        .count()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("fep.marketdata.snapshots.persisted")
        .tag("provider", "REPLAY")
        .tag("source_mode", "REPLAY")
        .counter()
        .count()).isEqualTo(1.0d);
  }
}
