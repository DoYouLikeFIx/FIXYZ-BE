package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
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
    List<CorebankOppositeBookQueryService.OppositeBookEntry> oppositeBook = List.of(
        candidate(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70000.0000", "2026-03-01T09:00:00Z"),
        candidate(12L, 102L, "sell-2", "005930", "SELL", "1.0000", "70000.0000", "2026-03-01T09:01:00Z"),
        candidate(13L, 103L, "sell-3", "005930", "SELL", "3.0000", "70100.0000", "2026-03-01T09:02:00Z")
    );

    MarketOrderSweepMatcher.MarketSweepMatchResult result = matcher.match(new BigDecimal("4.0000"), oppositeBook);

    assertThat(result.rejected()).isFalse();
    assertThat(result.executionResult()).isEqualTo("FILLED");
    assertThat(result.executedQty()).isEqualByComparingTo("4.0000");
    assertThat(result.leavesQty()).isEqualByComparingTo("0.0000");
    assertThat(result.executedPrice()).isEqualByComparingTo("70025.0000");
    assertThat(result.fills()).extracting(MarketOrderSweepMatcher.MarketSweepFill::makerClOrdId)
        .containsExactly("sell-1", "sell-2", "sell-3");
    assertThat(result.fills()).extracting(MarketOrderSweepMatcher.MarketSweepFill::executedQty)
        .containsExactly(
            new BigDecimal("2.0000"),
            new BigDecimal("1.0000"),
            new BigDecimal("1.0000")
        );
  }

  @Test
  void shouldReturnPartialFillWhenLiquidityIsInsufficient() {
    List<CorebankOppositeBookQueryService.OppositeBookEntry> oppositeBook = List.of(
        candidate(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70000.0000", "2026-03-01T09:00:00Z"),
        candidate(12L, 102L, "sell-2", "005930", "SELL", "1.5000", "70100.0000", "2026-03-01T09:01:00Z")
    );

    MarketOrderSweepMatcher.MarketSweepMatchResult result = matcher.match(new BigDecimal("5.0000"), oppositeBook);

    assertThat(result.rejected()).isFalse();
    assertThat(result.executionResult()).isEqualTo("PARTIALLY_FILLED");
    assertThat(result.executedQty()).isEqualByComparingTo("3.5000");
    assertThat(result.leavesQty()).isEqualByComparingTo("1.5000");
    assertThat(result.executedPrice()).isEqualByComparingTo("70042.8571");
    assertThat(result.fills()).hasSize(2);
  }

  @Test
  void shouldRejectWhenNoLiquidityExists() {
    MarketOrderSweepMatcher.MarketSweepMatchResult result = matcher.match(
        new BigDecimal("3.0000"),
        List.of()
    );

    assertThat(result.rejected()).isTrue();
    assertThat(result.rejectCode()).isEqualTo(ErrorCode.ORD_NO_LIQUIDITY);
    assertThat(result.executionResult()).isEqualTo("REJECTED");
    assertThat(result.executedQty()).isEqualByComparingTo("0.0000");
    assertThat(result.leavesQty()).isEqualByComparingTo("3.0000");
    assertThat(result.executedPrice()).isNull();
    assertThat(result.fills()).isEmpty();
  }

  @Test
  void shouldRejectNonPositiveRequestedQuantity() {
    assertThatThrownBy(() -> matcher.match(BigDecimal.ZERO, List.of()))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORD_INVALID_REQUEST);
          assertThat(ex.getMessage()).contains("orderQty is required");
        });
  }

  private CorebankOppositeBookQueryService.OppositeBookEntry candidate(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String remainingQty,
      String limitPrice,
      String priorityTime
  ) {
    return new CorebankOppositeBookQueryService.OppositeBookEntry(
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
