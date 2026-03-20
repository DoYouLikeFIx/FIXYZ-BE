package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.config.CorebankMarketDataProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class QuoteFreshnessPolicy {

  private final CorebankMarketDataProperties properties;
  private final Clock clock;

  @Autowired
  public QuoteFreshnessPolicy(CorebankMarketDataProperties properties, ObjectProvider<Clock> clockProvider) {
    this(properties, clockProvider.getIfAvailable(Clock::systemUTC));
  }

  QuoteFreshnessPolicy(CorebankMarketDataProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public QuoteFreshnessDecision evaluate(Instant quoteAsOf) {
    if (quoteAsOf == null) {
      throw new BusinessException(ErrorCode.STALE_QUOTE, "quoteAsOf is required");
    }
    long snapshotAgeMs = snapshotAgeMs(quoteAsOf);
    return new QuoteFreshnessDecision(
        snapshotAgeMs <= properties.getMaxQuoteAgeMs(),
        snapshotAgeMs,
        properties.getMaxQuoteAgeMs()
    );
  }

  public void validateFresh(Instant quoteAsOf) {
    QuoteFreshnessDecision decision = evaluate(quoteAsOf);
    if (decision.stale()) {
      throw new BusinessException(ErrorCode.STALE_QUOTE, ErrorCode.STALE_QUOTE.defaultMessage());
    }
  }

  private long snapshotAgeMs(Instant quoteAsOf) {
    long elapsedMs = Duration.between(quoteAsOf, Instant.now(clock)).toMillis();
    return Math.max(0L, elapsedMs);
  }
}
