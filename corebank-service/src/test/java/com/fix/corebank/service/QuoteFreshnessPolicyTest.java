package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.config.CorebankMarketDataProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuoteFreshnessPolicyTest {

  private QuoteFreshnessPolicy quoteFreshnessPolicy;

  @BeforeEach
  void setUp() {
    CorebankMarketDataProperties properties = new CorebankMarketDataProperties();
    properties.setMaxQuoteAgeMs(5_000L);
    quoteFreshnessPolicy = new QuoteFreshnessPolicy(
        properties,
        Clock.fixed(Instant.parse("2026-03-20T09:00:05Z"), ZoneOffset.UTC)
    );
  }

  @Test
  void shouldTreatExactThresholdAsFresh() {
    QuoteFreshnessDecision decision = quoteFreshnessPolicy.evaluate(Instant.parse("2026-03-20T09:00:00Z"));

    assertThat(decision.fresh()).isTrue();
    assertThat(decision.snapshotAgeMs()).isEqualTo(5_000L);
    assertThat(decision.maxQuoteAgeMs()).isEqualTo(5_000L);
  }

  @Test
  void shouldTreatOverThresholdAsStale() {
    QuoteFreshnessDecision decision = quoteFreshnessPolicy.evaluate(Instant.parse("2026-03-20T08:59:59.999Z"));

    assertThat(decision.fresh()).isFalse();
    assertThat(decision.stale()).isTrue();
    assertThat(decision.snapshotAgeMs()).isEqualTo(5_001L);
  }

  @Test
  void shouldClampFutureQuoteAgeToZero() {
    QuoteFreshnessDecision decision = quoteFreshnessPolicy.evaluate(Instant.parse("2026-03-20T09:00:06Z"));

    assertThat(decision.fresh()).isTrue();
    assertThat(decision.snapshotAgeMs()).isZero();
  }

  @Test
  void shouldRaiseStaleQuoteWhenQuoteAsOfMissing() {
    assertThatThrownBy(() -> quoteFreshnessPolicy.evaluate(null))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STALE_QUOTE);
          assertThat(ex.getMessage()).isEqualTo("quoteAsOf is required");
        });
  }
}
