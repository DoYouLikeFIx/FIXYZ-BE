package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.common.error.ErrorCode;
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
    CorebankMatchingEngine.MatchResult result = matchingEngine.match(
        CorebankMatchingEngine.MatchRequest.limit(
            "BUY",
            new BigDecimal("3.0000"),
            new BigDecimal("70000.0000"),
            List.of(
                bookEntry(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70100.0000", "2026-03-01T09:00:00Z"),
                bookEntry(12L, 102L, "sell-2", "005930", "SELL", "2.0000", "70200.0000", "2026-03-01T09:01:00Z")
            )
        )
    );

    assertThat(result.resting()).isTrue();
    assertThat(result.rejected()).isFalse();
    assertThat(result.fills()).isEmpty();
    assertThat(result.totalExecutedQty()).isEqualByComparingTo("0.0000");
    assertThat(result.leavesQty()).isEqualByComparingTo("3.0000");
    assertThat(result.weightedAvgPrice()).isNull();
    assertThat(result.executionResult()).isNull();
  }

  @Test
  void shouldMatchLimitOrderInStrictPriceTimeOrderUntilPriceStopsCrossing() {
    CorebankMatchingEngine.MatchResult result = matchingEngine.match(
        CorebankMatchingEngine.MatchRequest.limit(
            "BUY",
            new BigDecimal("4.0000"),
            new BigDecimal("70100.0000"),
            List.of(
                bookEntry(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70000.0000", "2026-03-01T09:00:00Z"),
                bookEntry(12L, 102L, "sell-2", "005930", "SELL", "2.0000", "70100.0000", "2026-03-01T09:01:00Z"),
                bookEntry(13L, 103L, "sell-3", "005930", "SELL", "3.0000", "70200.0000", "2026-03-01T09:02:00Z")
            )
        )
    );

    assertThat(result.rejected()).isFalse();
    assertThat(result.resting()).isFalse();
    assertThat(result.decision()).isEqualTo(CorebankMatchingEngine.MatchDecision.FILLED);
    assertThat(result.executionResult()).isEqualTo("FILLED");
    assertThat(result.totalExecutedQty()).isEqualByComparingTo("4.0000");
    assertThat(result.leavesQty()).isEqualByComparingTo("0.0000");
    assertThat(result.weightedAvgPrice()).isEqualByComparingTo("70050.0000");
    assertThat(result.fills()).extracting(CorebankMatchingEngine.MatchFill::makerClOrdId)
        .containsExactly("sell-1", "sell-2");
  }

  @Test
  void shouldReturnPartialFillForLimitOrderWhenPriceStopsCrossingWithLeavesRemaining() {
    CorebankMatchingEngine.MatchResult result = matchingEngine.match(
        CorebankMatchingEngine.MatchRequest.limit(
            "BUY",
            new BigDecimal("5.0000"),
            new BigDecimal("70100.0000"),
            List.of(
                bookEntry(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70000.0000", "2026-03-01T09:00:00Z"),
                bookEntry(12L, 102L, "sell-2", "005930", "SELL", "2.0000", "70100.0000", "2026-03-01T09:01:00Z"),
                bookEntry(13L, 103L, "sell-3", "005930", "SELL", "3.0000", "70200.0000", "2026-03-01T09:02:00Z")
            )
        )
    );

    assertThat(result.rejected()).isFalse();
    assertThat(result.resting()).isFalse();
    assertThat(result.decision()).isEqualTo(CorebankMatchingEngine.MatchDecision.PARTIALLY_FILLED);
    assertThat(result.executionResult()).isEqualTo("PARTIALLY_FILLED");
    assertThat(result.totalExecutedQty()).isEqualByComparingTo("4.0000");
    assertThat(result.leavesQty()).isEqualByComparingTo("1.0000");
    assertThat(result.weightedAvgPrice()).isEqualByComparingTo("70050.0000");
    assertThat(result.fills()).extracting(CorebankMatchingEngine.MatchFill::makerClOrdId)
        .containsExactly("sell-1", "sell-2");
  }

  @Test
  void shouldReturnPartialFillForMarketOrderWhenLiquidityIsInsufficient() {
    CorebankMatchingEngine.MatchResult result = matchingEngine.match(
        CorebankMatchingEngine.MatchRequest.market(
            new BigDecimal("5.0000"),
            List.of(
                bookEntry(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70000.0000", "2026-03-01T09:00:00Z"),
                bookEntry(12L, 102L, "sell-2", "005930", "SELL", "1.5000", "70100.0000", "2026-03-01T09:01:00Z")
            )
        )
    );

    assertThat(result.decision()).isEqualTo(CorebankMatchingEngine.MatchDecision.PARTIALLY_FILLED);
    assertThat(result.executionResult()).isEqualTo("PARTIALLY_FILLED");
    assertThat(result.totalExecutedQty()).isEqualByComparingTo("3.5000");
    assertThat(result.leavesQty()).isEqualByComparingTo("1.5000");
    assertThat(result.weightedAvgPrice()).isEqualByComparingTo("70042.8571");
  }

  @Test
  void shouldRejectMarketOrderWhenOppositeBookIsEmpty() {
    CorebankMatchingEngine.MatchResult result = matchingEngine.match(
        CorebankMatchingEngine.MatchRequest.market(new BigDecimal("3.0000"), List.of())
    );

    assertThat(result.rejected()).isTrue();
    assertThat(result.decision()).isEqualTo(CorebankMatchingEngine.MatchDecision.REJECTED);
    assertThat(result.rejectCode()).isEqualTo(ErrorCode.ORD_NO_LIQUIDITY);
    assertThat(result.fills()).isEmpty();
    assertThat(result.totalExecutedQty()).isEqualByComparingTo("0.0000");
    assertThat(result.leavesQty()).isEqualByComparingTo("3.0000");
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
}
