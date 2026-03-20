package com.fix.corebank.config;

import com.fix.common.fep.FepQuoteSourceMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "corebank.market-data")
public class CorebankMarketDataProperties {

  private long maxQuoteAgeMs = 30_000L;
  private FepQuoteSourceMode quoteSourceMode = FepQuoteSourceMode.LIVE;

  public long getMaxQuoteAgeMs() {
    return maxQuoteAgeMs;
  }

  public void setMaxQuoteAgeMs(long maxQuoteAgeMs) {
    if (maxQuoteAgeMs <= 0L) {
      throw new IllegalArgumentException("corebank.market-data.max-quote-age-ms must be positive");
    }
    this.maxQuoteAgeMs = maxQuoteAgeMs;
  }

  public FepQuoteSourceMode getQuoteSourceMode() {
    return quoteSourceMode;
  }

  public void setQuoteSourceMode(FepQuoteSourceMode quoteSourceMode) {
    if (quoteSourceMode == null) {
      throw new IllegalArgumentException("corebank.market-data.quote-source-mode is required");
    }
    this.quoteSourceMode = quoteSourceMode;
  }
}
