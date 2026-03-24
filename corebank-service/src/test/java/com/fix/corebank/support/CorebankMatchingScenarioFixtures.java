package com.fix.corebank.support;

import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepOrderType;
import com.fix.corebank.service.CorebankMatchingEngine;
import com.fix.corebank.service.CorebankOppositeBookQueryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public final class CorebankMatchingScenarioFixtures {

  private static final int MONEY_SCALE = 4;

  private CorebankMatchingScenarioFixtures() {
  }

  public static List<CanonicalMatchingScenario> story119CanonicalScenarios() {
    return List.of(
        limitCross(),
        limitPartial(),
        limitNonCross(),
        marketSweep(),
        marketPartial(),
        marketNoLiquidity()
    );
  }

  public static CanonicalMatchingScenario limitCross() {
    return new CanonicalMatchingScenario(
        CanonicalScenarioId.LIMIT_CROSS,
        FepOrderType.LIMIT.name(),
        "BUY",
        "005930",
        amount("4.0000"),
        amount("70100.0000"),
        List.of(
            bookEntry(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70000.0000", "2026-03-01T09:00:00Z"),
            bookEntry(12L, 102L, "sell-2", "005930", "SELL", "2.0000", "70100.0000", "2026-03-01T09:01:00Z"),
            bookEntry(13L, 103L, "sell-3", "005930", "SELL", "3.0000", "70200.0000", "2026-03-01T09:02:00Z")
        ),
        new ExpectedOutcome(
            CorebankMatchingEngine.MatchDecision.FILLED,
            amount("4.0000"),
            amount("0.0000"),
            amount("70050.0000"),
            null,
            List.of(
                fill("sell-1", "2.0000", "70000.0000", "0.0000"),
                fill("sell-2", "2.0000", "70100.0000", "0.0000")
            )
        )
    );
  }

  public static CanonicalMatchingScenario limitNonCross() {
    return new CanonicalMatchingScenario(
        CanonicalScenarioId.LIMIT_NON_CROSS,
        FepOrderType.LIMIT.name(),
        "BUY",
        "005930",
        amount("3.0000"),
        amount("70000.0000"),
        List.of(
            bookEntry(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70100.0000", "2026-03-01T09:00:00Z"),
            bookEntry(12L, 102L, "sell-2", "005930", "SELL", "2.0000", "70200.0000", "2026-03-01T09:01:00Z")
        ),
        new ExpectedOutcome(
            CorebankMatchingEngine.MatchDecision.RESTING,
            amount("0.0000"),
            amount("3.0000"),
            null,
            null,
            List.of()
        )
    );
  }

  public static CanonicalMatchingScenario limitPartial() {
    return new CanonicalMatchingScenario(
        CanonicalScenarioId.LIMIT_PARTIAL,
        FepOrderType.LIMIT.name(),
        "BUY",
        "005930",
        amount("5.0000"),
        amount("70100.0000"),
        List.of(
            bookEntry(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70000.0000", "2026-03-01T09:00:00Z"),
            bookEntry(12L, 102L, "sell-2", "005930", "SELL", "2.0000", "70100.0000", "2026-03-01T09:01:00Z"),
            bookEntry(13L, 103L, "sell-3", "005930", "SELL", "3.0000", "70200.0000", "2026-03-01T09:02:00Z")
        ),
        new ExpectedOutcome(
            CorebankMatchingEngine.MatchDecision.PARTIALLY_FILLED,
            amount("4.0000"),
            amount("1.0000"),
            amount("70050.0000"),
            null,
            List.of(
                fill("sell-1", "2.0000", "70000.0000", "0.0000"),
                fill("sell-2", "2.0000", "70100.0000", "0.0000")
            )
        )
    );
  }

  public static CanonicalMatchingScenario marketSweep() {
    return new CanonicalMatchingScenario(
        CanonicalScenarioId.MARKET_SWEEP,
        FepOrderType.MARKET.name(),
        "BUY",
        "005930",
        amount("4.0000"),
        null,
        List.of(
            bookEntry(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70000.0000", "2026-03-01T09:00:00Z"),
            bookEntry(12L, 102L, "sell-2", "005930", "SELL", "1.0000", "70000.0000", "2026-03-01T09:01:00Z"),
            bookEntry(13L, 103L, "sell-3", "005930", "SELL", "3.0000", "70100.0000", "2026-03-01T09:02:00Z")
        ),
        new ExpectedOutcome(
            CorebankMatchingEngine.MatchDecision.FILLED,
            amount("4.0000"),
            amount("0.0000"),
            amount("70025.0000"),
            null,
            List.of(
                fill("sell-1", "2.0000", "70000.0000", "0.0000"),
                fill("sell-2", "1.0000", "70000.0000", "0.0000"),
                fill("sell-3", "1.0000", "70100.0000", "2.0000")
            )
        )
    );
  }

  public static CanonicalMatchingScenario marketPartial() {
    return new CanonicalMatchingScenario(
        CanonicalScenarioId.MARKET_PARTIAL,
        FepOrderType.MARKET.name(),
        "BUY",
        "005930",
        amount("5.0000"),
        null,
        List.of(
            bookEntry(11L, 101L, "sell-1", "005930", "SELL", "2.0000", "70000.0000", "2026-03-01T09:00:00Z"),
            bookEntry(12L, 102L, "sell-2", "005930", "SELL", "1.5000", "70100.0000", "2026-03-01T09:01:00Z")
        ),
        new ExpectedOutcome(
            CorebankMatchingEngine.MatchDecision.PARTIALLY_FILLED,
            amount("3.5000"),
            amount("1.5000"),
            amount("70042.8571"),
            null,
            List.of(
                fill("sell-1", "2.0000", "70000.0000", "0.0000"),
                fill("sell-2", "1.5000", "70100.0000", "0.0000")
            )
        )
    );
  }

  public static CanonicalMatchingScenario marketNoLiquidity() {
    return new CanonicalMatchingScenario(
        CanonicalScenarioId.MARKET_NO_LIQUIDITY,
        FepOrderType.MARKET.name(),
        "BUY",
        "005930",
        amount("3.0000"),
        null,
        List.of(),
        new ExpectedOutcome(
            CorebankMatchingEngine.MatchDecision.REJECTED,
            amount("0.0000"),
            amount("3.0000"),
            null,
            ErrorCode.ORD_NO_LIQUIDITY,
            List.of()
        )
    );
  }

  private static CanonicalBookEntry bookEntry(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      String remainingQty,
      String limitPrice,
      String priorityTime
  ) {
    return new CanonicalBookEntry(
        orderId,
        accountId,
        clOrdId,
        symbol,
        side,
        amount(remainingQty),
        amount(limitPrice),
        Instant.parse(priorityTime),
        "NEW"
    );
  }

  private static ExpectedFill fill(
      String makerClOrdId,
      String executedQty,
      String executedPrice,
      String remainingMakerQty
  ) {
    return new ExpectedFill(
        makerClOrdId,
        amount(executedQty),
        amount(executedPrice),
        amount(remainingMakerQty)
    );
  }

  private static BigDecimal amount(String value) {
    return new BigDecimal(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  public enum CanonicalScenarioId {
    LIMIT_CROSS,
    LIMIT_PARTIAL,
    LIMIT_NON_CROSS,
    MARKET_SWEEP,
    MARKET_PARTIAL,
    MARKET_NO_LIQUIDITY
  }

  public record CanonicalMatchingScenario(
      CanonicalScenarioId id,
      String orderType,
      String side,
      String symbol,
      BigDecimal orderQty,
      BigDecimal limitPrice,
      List<CanonicalBookEntry> oppositeBook,
      ExpectedOutcome expected
  ) {
    public CanonicalMatchingScenario {
      oppositeBook = List.copyOf(oppositeBook);
    }

    public CorebankMatchingEngine.MatchRequest toMatchRequest() {
      List<CorebankMatchingEngine.MatchBookEntry> entries = oppositeBook.stream()
          .map(CanonicalBookEntry::toMatchBookEntry)
          .toList();
      if (FepOrderType.MARKET.name().equals(orderType)) {
        return CorebankMatchingEngine.MatchRequest.market(orderQty, entries);
      }
      return CorebankMatchingEngine.MatchRequest.limit(side, orderQty, limitPrice, entries);
    }

    public List<CorebankOppositeBookQueryService.OppositeBookEntry> toOppositeBookEntries() {
      return oppositeBook.stream()
          .map(CanonicalBookEntry::toOppositeBookEntry)
          .toList();
    }
  }

  public record CanonicalBookEntry(
      Long orderId,
      Long accountId,
      String clOrdId,
      String symbol,
      String side,
      BigDecimal remainingQty,
      BigDecimal limitPrice,
      Instant priorityTime,
      String status
  ) {
    public CorebankMatchingEngine.MatchBookEntry toMatchBookEntry() {
      return new CorebankMatchingEngine.MatchBookEntry(
          orderId,
          accountId,
          clOrdId,
          symbol,
          side,
          remainingQty,
          limitPrice,
          priorityTime,
          status
      );
    }

    public CorebankOppositeBookQueryService.OppositeBookEntry toOppositeBookEntry() {
      return new CorebankOppositeBookQueryService.OppositeBookEntry(
          orderId,
          accountId,
          clOrdId,
          symbol,
          side,
          remainingQty,
          limitPrice,
          priorityTime,
          status
      );
    }
  }

  public record ExpectedOutcome(
      CorebankMatchingEngine.MatchDecision decision,
      BigDecimal totalExecutedQty,
      BigDecimal leavesQty,
      BigDecimal weightedAvgPrice,
      ErrorCode rejectCode,
      List<ExpectedFill> fills
  ) {
    public ExpectedOutcome {
      fills = List.copyOf(fills);
    }

    public String matchingEngineExecutionResult() {
      return switch (decision) {
        case FILLED, PARTIALLY_FILLED -> decision.name();
        default -> null;
      };
    }

    public String marketSweepExecutionResult() {
      return switch (decision) {
        case REJECTED -> "REJECTED";
        case FILLED, PARTIALLY_FILLED -> decision.name();
        default -> null;
      };
    }
  }

  public record ExpectedFill(
      String makerClOrdId,
      BigDecimal executedQty,
      BigDecimal executedPrice,
      BigDecimal remainingMakerQty
  ) {
  }
}
