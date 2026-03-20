package com.fix.fepgateway.config;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FepMarketDataConfigurationValidator {

  private final FepMarketDataProperties properties;

  public FepMarketDataConfigurationValidator(FepMarketDataProperties properties) {
    this.properties = properties;
    validate();
  }

  private void validate() {
    if (properties.isKisStreamingModeEnabled()) {
      validateKisStreamingConfiguration();
    }
    if (properties.isReplayModeEnabled()) {
      validateReplayConfiguration();
    }
  }

  private void validateKisStreamingConfiguration() {
    require(
        !isBlank(properties.getKis().getAppKey()),
        "FEP_MARKETDATA_KIS_APP_KEY is required for KIS LIVE/DELAYED mode"
    );
    require(
        !isBlank(properties.getKis().getAppSecret()),
        "FEP_MARKETDATA_KIS_APP_SECRET is required for KIS LIVE/DELAYED mode"
    );
    require(isAllowed(properties.getKis().getEnv(), List.of("paper", "demo", "real")),
        "FEP_MARKETDATA_KIS_ENV must be one of: paper, demo, real");
    require("H0STCNT0".equals(properties.getKis().getWs().getTrId()),
        "FEP_MARKETDATA_KIS_WS_TR_ID must be H0STCNT0 for the current project scope");
    require(isAllowed(properties.getKis().getWs().getCusttype(), List.of("P", "B")),
        "FEP_MARKETDATA_KIS_WS_CUSTTYPE must be one of: P, B");
    require(!properties.getKis().getWs().getSymbols().isEmpty(),
        "FEP_MARKETDATA_KIS_SYMBOLS must contain at least one symbol");
    if (properties.isKisDelayedModeEnabled()) {
      require(
          properties.getDelayed().getDelayMs() > 0,
          "fep.marketdata.delayed.delay-ms must be greater than zero for DELAYED mode"
      );
      require(
          properties.getDelayed().getDrainIntervalMs() > 0,
          "fep.marketdata.delayed.drain-interval-ms must be greater than zero for DELAYED mode"
      );
    }
  }

  private void validateReplayConfiguration() {
    require(!isBlank(properties.getReplay().getSeed()),
        "fep.marketdata.replay.seed must not be blank for REPLAY mode");
    require(properties.getReplay().getSpeedFactor() != null,
        "fep.marketdata.replay.speed-factor must not be null for REPLAY mode");
    require(properties.getReplay().getSpeedFactor().signum() > 0,
        "fep.marketdata.replay.speed-factor must be greater than zero for REPLAY mode");
    require(properties.getReplay().getStartOffset() >= 0,
        "fep.marketdata.replay.start-offset must be zero or positive for REPLAY mode");
    require(properties.getReplay().getDrainIntervalMs() > 0,
        "fep.marketdata.replay.drain-interval-ms must be greater than zero for REPLAY mode");
    require(!properties.getReplay().getSymbols().isEmpty(),
        "fep.marketdata.replay.symbols must contain at least one symbol for REPLAY mode");
  }

  private static boolean isAllowed(String value, List<String> allowed) {
    return value != null && allowed.stream().anyMatch(option -> option.equalsIgnoreCase(value));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static void require(boolean expression, String message) {
    if (!expression) {
      throw new IllegalStateException(message);
    }
  }
}
