package com.fix.corebank.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fix.common.fep.FepOrderType;
import com.fix.corebank.service.CorebankMatchingEngine;
import com.fix.corebank.service.MarketOrderSweepMatcher;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.CanonicalMatchingScenario;
import com.fix.corebank.support.CorebankMatchingScenarioFixtures.ExpectedOutcome;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CorebankMatchingContractTest {

  private CorebankMatchingEngine matchingEngine;
  private MarketOrderSweepMatcher marketOrderSweepMatcher;

  @BeforeEach
  void setUp() {
    matchingEngine = new CorebankMatchingEngine();
    marketOrderSweepMatcher = new MarketOrderSweepMatcher(matchingEngine);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("canonicalScenarioMatrix")
  void shouldEnforceDeterministicOutcomeContractAcrossCanonicalMatchingScenarios(
      String scenarioId,
      CanonicalMatchingScenario scenario
  ) {
    CorebankMatchingEngine.MatchResult result = matchingEngine.match(scenario.toMatchRequest());

    assertThat(result.resting()).isEqualTo(scenario.expected().decision() == CorebankMatchingEngine.MatchDecision.RESTING);
    assertThat(result.rejected()).isEqualTo(scenario.expected().decision() == CorebankMatchingEngine.MatchDecision.REJECTED);
    assertMatchResultMatchesExpected(result, scenario.expected());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("marketScenarioMatrix")
  void shouldEnforceMarketSweepContractAcrossCanonicalMarketScenarios(
      String scenarioId,
      CanonicalMatchingScenario scenario
  ) {
    MarketOrderSweepMatcher.MarketSweepMatchResult result =
        marketOrderSweepMatcher.match(scenario.orderQty(), scenario.toOppositeBookEntries());

    assertThat(result.rejected()).isEqualTo(scenario.expected().decision() == CorebankMatchingEngine.MatchDecision.REJECTED);
    assertSweepResultMatchesExpected(result, scenario.expected());
  }

  private static Stream<Arguments> canonicalScenarioMatrix() {
    return CorebankMatchingScenarioFixtures.story119CanonicalScenarios().stream()
        .map(scenario -> Arguments.of(scenario.id().name(), scenario));
  }

  private static Stream<Arguments> marketScenarioMatrix() {
    return CorebankMatchingScenarioFixtures.story119CanonicalScenarios().stream()
        .filter(scenario -> FepOrderType.MARKET.name().equals(scenario.orderType()))
        .map(scenario -> Arguments.of(scenario.id().name(), scenario));
  }

  private void assertMatchResultMatchesExpected(
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
