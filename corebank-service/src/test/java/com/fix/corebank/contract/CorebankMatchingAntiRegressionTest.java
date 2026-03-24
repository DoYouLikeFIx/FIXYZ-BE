package com.fix.corebank.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fix.corebank.service.CorebankMatchingEngine;
import com.fix.corebank.service.MarketOrderSweepMatcher;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.CanonicalMatchingScenario;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.ExpectedOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorebankMatchingAntiRegressionTest {

  private CorebankMatchingEngine matchingEngine;
  private MarketOrderSweepMatcher marketOrderSweepMatcher;

  @BeforeEach
  void setUp() {
    matchingEngine = new CorebankMatchingEngine();
    marketOrderSweepMatcher = new MarketOrderSweepMatcher(matchingEngine);
  }

  @Test
  void shouldKeepLimitNonCrossScenarioRestingInsteadOfForcingFilled() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.limitNonCross();

    CorebankMatchingEngine.MatchResult result = matchingEngine.match(scenario.toMatchRequest());

    assertThat(result.decision()).isEqualTo(CorebankMatchingEngine.MatchDecision.RESTING);
    assertThat(result.executionResult()).isNull();
    assertThat(result.totalExecutedQty()).isEqualByComparingTo("0.0000");
    assertThat(result.leavesQty()).isEqualByComparingTo(scenario.orderQty());
    assertThat(result.fills()).isEmpty();
  }

  @Test
  void shouldRejectMarketNoLiquidityScenarioInsteadOfSynthesizingImmediateFill() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.marketNoLiquidity();

    MarketOrderSweepMatcher.MarketSweepMatchResult result =
        marketOrderSweepMatcher.match(scenario.orderQty(), scenario.toOppositeBookEntries());

    assertThat(result.rejected()).isTrue();
    assertThat(result.executionResult()).isEqualTo("REJECTED");
    assertThat(result.executedQty()).isEqualByComparingTo("0.0000");
    assertThat(result.leavesQty()).isEqualByComparingTo(scenario.orderQty());
    assertThat(result.executedPrice()).isNull();
    assertThat(result.fills()).isEmpty();
  }

  @Test
  void shouldKeepMarketPartialScenarioPartiallyFilledInsteadOfPromotingToFilled() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.marketPartial();

    MarketOrderSweepMatcher.MarketSweepMatchResult result =
        marketOrderSweepMatcher.match(scenario.orderQty(), scenario.toOppositeBookEntries());

    assertThat(result.executionResult()).isEqualTo("PARTIALLY_FILLED");
    assertThat(result.executedQty()).isEqualByComparingTo(scenario.expected().totalExecutedQty());
    assertThat(result.leavesQty()).isEqualByComparingTo(scenario.expected().leavesQty());
    assertThat(result.executedPrice()).isEqualByComparingTo(scenario.expected().weightedAvgPrice());
    assertSweepFillBreakdown(result, scenario.expected());
  }

  @Test
  void shouldPreserveMultiLevelSweepBreakdownInsteadOfCollapsingToSingleFill() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.marketSweep();

    MarketOrderSweepMatcher.MarketSweepMatchResult result =
        marketOrderSweepMatcher.match(scenario.orderQty(), scenario.toOppositeBookEntries());

    assertThat(result.executionResult()).isEqualTo("FILLED");
    assertThat(result.executedQty()).isEqualByComparingTo(scenario.expected().totalExecutedQty());
    assertThat(result.fills()).hasSize(3);
    assertSweepFillBreakdown(result, scenario.expected());
  }

  private void assertSweepFillBreakdown(
      MarketOrderSweepMatcher.MarketSweepMatchResult result,
      ExpectedOutcome expected
  ) {
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
