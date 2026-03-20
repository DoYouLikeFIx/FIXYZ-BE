package com.fix.fepgateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class FepMarketDataConfigurationValidatorTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(TestConfig.class);

  @Test
  void shouldAllowDefaultNonKisConfiguration() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(FepMarketDataProperties.class);
    });
  }

  @Test
  void shouldFailWhenKisLiveModeIsMissingCredentials() {
    contextRunner
        .withPropertyValues(
            "fep.marketdata.provider=KIS",
            "fep.marketdata.source-mode=LIVE"
        )
        .run(context -> {
          assertThat(context).hasFailed();
          Throwable rootCause = rootCauseOf(context.getStartupFailure());
          assertThat(rootCause)
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("FEP_MARKETDATA_KIS_APP_KEY");
        });
  }

  @Test
  void shouldBindKisLiveConfigurationFromEnvironmentShape() {
    contextRunner
        .withPropertyValues(
            "fep.marketdata.provider=KIS",
            "fep.marketdata.source-mode=LIVE",
            "fep.marketdata.kis.env=paper",
            "fep.marketdata.kis.app-key=test-app-key",
            "fep.marketdata.kis.app-secret=test-app-secret",
            "fep.marketdata.kis.ws.tr-id=H0STCNT0",
            "fep.marketdata.kis.ws.custtype=P",
            "fep.marketdata.kis.ws.symbols[0]=005930",
            "fep.marketdata.kis.ws.symbols[1]=000660"
        )
        .run(context -> {
          assertThat(context).hasNotFailed();
          FepMarketDataProperties properties = context.getBean(FepMarketDataProperties.class);
          assertThat(properties.isKisLiveModeEnabled()).isTrue();
          assertThat(properties.getKis().getEnv()).isEqualTo("paper");
          assertThat(properties.getKis().getWs().getSymbols()).containsExactly("005930", "000660");
        });
  }

  @Test
  void shouldAllowDemoAliasForPaperEnvironment() {
    contextRunner
        .withPropertyValues(
            "fep.marketdata.provider=KIS",
            "fep.marketdata.source-mode=LIVE",
            "fep.marketdata.kis.env=demo",
            "fep.marketdata.kis.app-key=test-app-key",
            "fep.marketdata.kis.app-secret=test-app-secret",
            "fep.marketdata.kis.ws.tr-id=H0STCNT0",
            "fep.marketdata.kis.ws.custtype=P",
            "fep.marketdata.kis.ws.symbols[0]=005930"
        )
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void shouldAllowDelayedModeWhenDelayConfigurationIsPositive() {
    contextRunner
        .withPropertyValues(
            "fep.marketdata.provider=KIS",
            "fep.marketdata.source-mode=DELAYED",
            "fep.marketdata.delayed.delay-ms=900000",
            "fep.marketdata.delayed.drain-interval-ms=1000",
            "fep.marketdata.kis.env=paper",
            "fep.marketdata.kis.app-key=test-app-key",
            "fep.marketdata.kis.app-secret=test-app-secret",
            "fep.marketdata.kis.ws.tr-id=H0STCNT0",
            "fep.marketdata.kis.ws.custtype=P",
            "fep.marketdata.kis.ws.symbols[0]=005930"
        )
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void shouldFailWhenDelayedModeHasNonPositiveDelay() {
    contextRunner
        .withPropertyValues(
            "fep.marketdata.provider=KIS",
            "fep.marketdata.source-mode=DELAYED",
            "fep.marketdata.delayed.delay-ms=0",
            "fep.marketdata.delayed.drain-interval-ms=1000",
            "fep.marketdata.kis.env=paper",
            "fep.marketdata.kis.app-key=test-app-key",
            "fep.marketdata.kis.app-secret=test-app-secret",
            "fep.marketdata.kis.ws.tr-id=H0STCNT0",
            "fep.marketdata.kis.ws.custtype=P",
            "fep.marketdata.kis.ws.symbols[0]=005930"
        )
        .run(context -> {
          assertThat(context).hasFailed();
          Throwable rootCause = rootCauseOf(context.getStartupFailure());
          assertThat(rootCause)
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("delay-ms");
        });
  }

  @Test
  void shouldAllowReplayModeWithDeterministicSettings() {
    contextRunner
        .withPropertyValues(
            "fep.marketdata.provider=REPLAY",
            "fep.marketdata.source-mode=REPLAY",
            "fep.marketdata.replay.seed=test-seed",
            "fep.marketdata.replay.speed-factor=1.5000",
            "fep.marketdata.replay.start-offset=0",
            "fep.marketdata.replay.drain-interval-ms=1000",
            "fep.marketdata.replay.symbols[0]=005930"
        )
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void shouldFailWhenReplayModeHasInvalidSpeedFactor() {
    contextRunner
        .withPropertyValues(
            "fep.marketdata.provider=REPLAY",
            "fep.marketdata.source-mode=REPLAY",
            "fep.marketdata.replay.seed=test-seed",
            "fep.marketdata.replay.speed-factor=0",
            "fep.marketdata.replay.start-offset=0",
            "fep.marketdata.replay.drain-interval-ms=1000",
            "fep.marketdata.replay.symbols[0]=005930"
        )
        .run(context -> {
          assertThat(context).hasFailed();
          Throwable rootCause = rootCauseOf(context.getStartupFailure());
          assertThat(rootCause)
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("speed-factor");
        });
  }

  @Configuration
  @EnableConfigurationProperties(FepMarketDataProperties.class)
  static class TestConfig {
    TestConfig(FepMarketDataProperties properties) {
      new FepMarketDataConfigurationValidator(properties);
    }
  }

  private static Throwable rootCauseOf(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
