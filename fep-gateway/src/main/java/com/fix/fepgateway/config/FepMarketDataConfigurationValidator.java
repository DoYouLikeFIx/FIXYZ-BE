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
    if (!properties.isKisLiveModeEnabled()) {
      return;
    }

    require(!isBlank(properties.getKis().getAppKey()), "FEP_MARKETDATA_KIS_APP_KEY is required for KIS LIVE mode");
    require(!isBlank(properties.getKis().getAppSecret()), "FEP_MARKETDATA_KIS_APP_SECRET is required for KIS LIVE mode");
    require(isAllowed(properties.getKis().getEnv(), List.of("paper", "demo", "real")),
        "FEP_MARKETDATA_KIS_ENV must be one of: paper, demo, real");
    require("H0STCNT0".equals(properties.getKis().getWs().getTrId()),
        "FEP_MARKETDATA_KIS_WS_TR_ID must be H0STCNT0 for the current project scope");
    require(isAllowed(properties.getKis().getWs().getCusttype(), List.of("P", "B")),
        "FEP_MARKETDATA_KIS_WS_CUSTTYPE must be one of: P, B");
    require(!properties.getKis().getWs().getSymbols().isEmpty(),
        "FEP_MARKETDATA_KIS_SYMBOLS must contain at least one symbol");
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
