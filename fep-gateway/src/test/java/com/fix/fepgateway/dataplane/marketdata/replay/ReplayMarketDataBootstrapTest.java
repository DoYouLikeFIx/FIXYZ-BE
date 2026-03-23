package com.fix.fepgateway.dataplane.marketdata.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.fepgateway.config.FepMarketDataProperties;
import com.fix.fepgateway.dataplane.marketdata.LiveMarketDataPersistencePort;
import com.fix.fepgateway.dataplane.marketdata.MarketDataEventSink;
import com.fix.fepgateway.dataplane.marketdata.MarketDataSubscriptionSpec;
import com.fix.fepgateway.dataplane.marketdata.NormalizedQuoteEvent;
import com.fix.fepgateway.dataplane.marketdata.ReplayCursorSpec;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class ReplayMarketDataBootstrapTest {

  @Test
  void shouldStartReplaySubscriptionsWhenReplayModeEnabled() throws Exception {
    RecordingReplayMarketDataAdapter adapter = new RecordingReplayMarketDataAdapter();
    ReplayMarketDataBootstrap bootstrap = new ReplayMarketDataBootstrap(replayProperties(), adapter);

    bootstrap.run(new DefaultApplicationArguments(new String[0]));

    assertThat(adapter.startedSubscriptions()).extracting(MarketDataSubscriptionSpec::subscriptionId)
        .containsExactly("replay-bootstrap-005930", "replay-bootstrap-000660");
    assertThat(adapter.startedSubscriptions()).extracting(MarketDataSubscriptionSpec::sourceMode)
        .containsOnly(FepQuoteSourceMode.REPLAY);
  }

  private FepMarketDataProperties replayProperties() {
    FepMarketDataProperties properties = new FepMarketDataProperties();
    properties.setProvider("REPLAY");
    properties.setSourceMode("REPLAY");
    properties.getReplay().setSeed("seed-1");
    properties.getReplay().setSpeedFactor(new BigDecimal("1.0000"));
    properties.getReplay().setStartOffset(0L);
    properties.getReplay().setDrainIntervalMs(1_000L);
    properties.getReplay().setSymbols(List.of("005930", "000660"));
    return properties;
  }

  private static final class RecordingReplayMarketDataAdapter extends ReplayMarketDataAdapter {

    private final List<MarketDataSubscriptionSpec> startedSubscriptions = new ArrayList<>();

    private RecordingReplayMarketDataAdapter() {
      super(
          new FepMarketDataProperties(),
          new NoOpPersistencePort(),
          new NoOpReplayCursorPersistencePort(),
          new ReplayQuoteEventGenerator()
      );
    }

    @Override
    public synchronized void start(MarketDataSubscriptionSpec subscriptionSpec, MarketDataEventSink eventSink) {
      startedSubscriptions.add(subscriptionSpec);
    }

    @Override
    public synchronized void stop(String subscriptionId) {
    }

    private List<MarketDataSubscriptionSpec> startedSubscriptions() {
      return startedSubscriptions;
    }
  }

  private static final class NoOpPersistencePort implements LiveMarketDataPersistencePort {
    @Override
    public void activateSubscription(MarketDataSubscriptionSpec subscriptionSpec) {
    }

    @Override
    public void deactivateSubscription(MarketDataSubscriptionSpec subscriptionSpec) {
    }

    @Override
    public void persistSnapshot(MarketDataSubscriptionSpec subscriptionSpec, NormalizedQuoteEvent event) {
    }
  }

  private static final class NoOpReplayCursorPersistencePort implements ReplayCursorPersistencePort {
    @Override
    public ReplayCursorSpec activate(ReplayCursorSpec replayCursorSpec) {
      return replayCursorSpec;
    }

    @Override
    public ReplayCursorSpec reset(ReplayCursorSpec replayCursorSpec) {
      return replayCursorSpec;
    }

    @Override
    public ReplayCursorSpec advance(String replayId, long nextCursorOffset) {
      return new ReplayCursorSpec(replayId, "seed-1", "005930", nextCursorOffset, new BigDecimal("1.0000"));
    }

    @Override
    public void pause(String replayId) {
    }

    @Override
    public void resume(String replayId) {
    }

    @Override
    public void stop(String replayId) {
    }

    @Override
    public java.util.Optional<ReplayCursorSpec> find(String replayId) {
      return java.util.Optional.empty();
    }
  }
}
