package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.CanonicalMatchingScenario;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.ExpectedOutcome;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketOrderSweepMatcherTest {

  private MarketOrderSweepMatcher matcher;

  @BeforeEach
  void setUp() {
    matcher = new MarketOrderSweepMatcher(new CorebankMatchingEngine());
  }

  @Test
  void shouldConsumeOppositeBookInStrictPriceTimeOrder() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.marketSweep();

    MarketOrderSweepMatcher.MarketSweepMatchResult result =
        matcher.match(scenario.orderQty(), scenario.toOppositeBookEntries());

    assertThat(result.rejected()).isFalse();
    assertSweepResultMatchesExpected(result, scenario.expected());
  }

  @Test
  void shouldReturnPartialFillWhenLiquidityIsInsufficient() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.marketPartial();

    MarketOrderSweepMatcher.MarketSweepMatchResult result =
        matcher.match(scenario.orderQty(), scenario.toOppositeBookEntries());

    assertThat(result.rejected()).isFalse();
    assertSweepResultMatchesExpected(result, scenario.expected());
  }

  @Test
  void shouldRejectWhenNoLiquidityExists() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.marketNoLiquidity();

    MarketOrderSweepMatcher.MarketSweepMatchResult result =
        matcher.match(scenario.orderQty(), scenario.toOppositeBookEntries());

    assertThat(result.rejected()).isTrue();
    assertSweepResultMatchesExpected(result, scenario.expected());
  }

  @Test
  void shouldRejectNonPositiveRequestedQuantity() {
    assertThatThrownBy(() -> matcher.match(BigDecimal.ZERO, List.of()))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORD_INVALID_REQUEST);
          assertThat(ex.getMessage()).contains("orderQty is required");
        });
  }

  private void assertSweepResultMatchesExpected(
      MarketOrderSweepMatcher.MarketSweepMatchResult result,
      ExpectedOutcome expected
  ) {
    assertThat(result.executionResult()).isEqualTo(expected.marketSweepExecutionResult());
    assertThat(result.rejectCode()).isEqualTo(expected.rejectCode());
    assertThat(result.executedQty()).isEqualByComparingTo(expected.totalExecutedQty());
    assertThat(result.leavesQty()).isEqualByComparingTo(expected.leavesQty());
    assertThat(result.executedPrice()).isEqualTo(expected.weightedAvgPrice());
    assertThat(result.fills())
        .extracting(
            MarketOrderSweepMatcher.MarketSweepFill::makerClOrdId,
            MarketOrderSweepMatcher.MarketSweepFill::executedQty,
            MarketOrderSweepMatcher.MarketSweepFill::executedPrice,
            MarketOrderSweepMatcher.MarketSweepFill::remainingMakerQty
        )
        .containsExactlyElementsOf(expected.fills().stream()
            .map(fill -> tuple(
                fill.makerClOrdId(),
                fill.executedQty(),
                fill.executedPrice(),
                fill.remainingMakerQty()
            ))
            .toList());
  }
}
