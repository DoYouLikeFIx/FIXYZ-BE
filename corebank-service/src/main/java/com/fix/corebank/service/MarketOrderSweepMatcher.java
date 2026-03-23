package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketOrderSweepMatcher {

  private final CorebankMatchingEngine matchingEngine;

  public MarketSweepMatchResult match(
      BigDecimal requestedQty,
      List<CorebankOppositeBookQueryService.OppositeBookEntry> oppositeBook
  ) {
    CorebankMatchingEngine.MatchResult result = matchingEngine.match(
        CorebankMatchingEngine.MatchRequest.market(
            requestedQty,
            oppositeBook == null
                ? List.of()
                : oppositeBook.stream()
                    .map(this::toEntry)
                    .toList()
        )
    );
    if (result.rejected()) {
      return MarketSweepMatchResult.rejected(result.leavesQty(), result.rejectCode());
    }
    return MarketSweepMatchResult.executed(
        result.fills().stream()
            .map(fill -> new MarketSweepFill(
                fill.makerOrderId(),
                fill.makerAccountId(),
                fill.makerClOrdId(),
                fill.symbol(),
                fill.side(),
                fill.executedQty(),
                fill.executedPrice(),
                fill.remainingMakerQty(),
                fill.priorityTime()
            ))
            .toList(),
        result.executionResult(),
        result.totalExecutedQty(),
        result.leavesQty(),
        result.weightedAvgPrice()
    );
  }

  private CorebankMatchingEngine.MatchBookEntry toEntry(CorebankOppositeBookQueryService.OppositeBookEntry candidate) {
    return new CorebankMatchingEngine.MatchBookEntry(
        candidate.orderId(),
        candidate.accountId(),
        candidate.clOrdId(),
        candidate.symbol(),
        candidate.side(),
        candidate.remainingQty(),
        candidate.limitPrice(),
        candidate.priorityTime(),
        candidate.status()
    );
  }

  public record MarketSweepFill(
      Long makerOrderId,
      Long makerAccountId,
      String makerClOrdId,
      String symbol,
      String side,
      BigDecimal executedQty,
      BigDecimal executedPrice,
      BigDecimal remainingMakerQty,
      Instant priorityTime
  ) {
  }

  public record MarketSweepMatchResult(
      List<MarketSweepFill> fills,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      ErrorCode rejectCode
  ) {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);

    private static MarketSweepMatchResult rejected(BigDecimal requestedQty, ErrorCode rejectCode) {
      return new MarketSweepMatchResult(List.of(), "REJECTED", ZERO, requestedQty, null, rejectCode);
    }

    private static MarketSweepMatchResult executed(
        List<MarketSweepFill> fills,
        String executionResult,
        BigDecimal executedQty,
        BigDecimal leavesQty,
        BigDecimal executedPrice
    ) {
      return new MarketSweepMatchResult(fills, executionResult, executedQty, leavesQty, executedPrice, null);
    }

    public boolean rejected() {
      return rejectCode != null;
    }
  }
}
