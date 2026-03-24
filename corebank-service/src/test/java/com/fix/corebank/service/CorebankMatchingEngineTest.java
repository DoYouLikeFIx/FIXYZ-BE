package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fix.corebank.support.CorebankMatchingScenarioFixtures;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.CanonicalMatchingScenario;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.ExpectedOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CorebankMatchingEngineTest {

  private CorebankMatchingEngine matchingEngine;

  @BeforeEach
  void setUp() {
    matchingEngine = new CorebankMatchingEngine();
  }

  @Test
  void shouldKeepLimitOrderRestingWhenNoPriceCrossExists() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.limitNonCross();

    CorebankMatchingEngine.MatchResult result = matchingEngine.match(scenario.toMatchRequest());

    assertThat(result.resting()).isTrue();
    assertThat(result.rejected()).isFalse();
    assertResultMatchesExpected(result, scenario.expected());
  }

  @Test
  void shouldMatchLimitOrderInStrictPriceTimeOrderUntilPriceStopsCrossing() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.limitCross();

    CorebankMatchingEngine.MatchResult result = matchingEngine.match(scenario.toMatchRequest());

    assertThat(result.rejected()).isFalse();
    assertThat(result.resting()).isFalse();
    assertResultMatchesExpected(result, scenario.expected());
  }

  @Test
  void shouldReturnPartialFillForLimitOrderWhenPriceStopsCrossingWithLeavesRemaining() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.limitPartial();

    CorebankMatchingEngine.MatchResult result = matchingEngine.match(scenario.toMatchRequest());

    assertThat(result.rejected()).isFalse();
    assertThat(result.resting()).isFalse();
    assertResultMatchesExpected(result, scenario.expected());
  }

  @Test
  void shouldReturnPartialFillForMarketOrderWhenLiquidityIsInsufficient() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.marketPartial();

    CorebankMatchingEngine.MatchResult result = matchingEngine.match(scenario.toMatchRequest());

    assertResultMatchesExpected(result, scenario.expected());
  }

  @Test
  void shouldRejectMarketOrderWhenOppositeBookIsEmpty() {
    CanonicalMatchingScenario scenario = CorebankMatchingScenarioFixtures.marketNoLiquidity();

    CorebankMatchingEngine.MatchResult result = matchingEngine.match(scenario.toMatchRequest());

    assertThat(result.rejected()).isTrue();
    assertResultMatchesExpected(result, scenario.expected());
  }

  @Test
  void shouldProduceIdenticalOutputForSameOrderedSnapshotAndInput() {
    CorebankMatchingEngine.MatchRequest request = CorebankMatchingEngine.MatchRequest.limit(
        "SELL",
        new BigDecimal("4.0000"),
        new BigDecimal("69900.0000"),
        List.of(
            bookEntry(21L, 201L, "buy-1", "005930", "BUY", "1.5000", "70100.0000", "2026-03-01T09:00:00Z"),
            bookEntry(22L, 202L, "buy-2", "005930", "BUY", "2.5000", "70000.0000", "2026-03-01T09:01:00Z")
        )
    );

    CorebankMatchingEngine.MatchResult first = matchingEngine.match(request);
    CorebankMatchingEngine.MatchResult second = matchingEngine.match(request);

    assertThat(first).isEqualTo(second);
  }

  private CorebankMatchingEngine.MatchBookEntry bookEntry(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String remainingQty,
      String limitPrice,
      String priorityTime
  ) {
    return new CorebankMatchingEngine.MatchBookEntry(
        orderId,
        accountId,
        clOrdId,
        symbol,
        side,
        new BigDecimal(remainingQty),
        new BigDecimal(limitPrice),
        Instant.parse(priorityTime),
        "NEW"
    );
  }

  private void assertResultMatchesExpected(
      CorebankMatchingEngine.MatchResult result,
      ExpectedOutcome expected
  ) {
    assertThat(result.decision()).isEqualTo(expected.decision());
    assertThat(result.executionResult()).isEqualTo(expected.matchingEngineExecutionResult());
    assertThat(result.rejectCode()).isEqualTo(expected.rejectCode());
    assertThat(result.totalExecutedQty()).isEqualByComparingTo(expected.totalExecutedQty());
    assertThat(result.leavesQty()).isEqualByComparingTo(expected.leavesQty());
    assertThat(result.weightedAvgPrice()).isEqualTo(expected.weightedAvgPrice());
    assertThat(result.fills())
        .extracting(
            CorebankMatchingEngine.MatchFill::makerClOrdId,
            CorebankMatchingEngine.MatchFill::executedQty,
            CorebankMatchingEngine.MatchFill::executedPrice,
            CorebankMatchingEngine.MatchFill::remainingMakerQty
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
