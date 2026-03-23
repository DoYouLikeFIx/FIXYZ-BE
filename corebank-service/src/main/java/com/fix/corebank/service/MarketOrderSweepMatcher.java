package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MarketOrderSweepMatcher {

  private static final int MONEY_SCALE = 4;
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

  public MarketSweepMatchResult match(
      BigDecimal requestedQty,
      List<CorebankOppositeBookQueryService.OppositeBookEntry> oppositeBook
  ) {
    BigDecimal normalizedRequestedQty = normalizePositive(requestedQty, "requestedQty is required");
    if (oppositeBook == null || oppositeBook.isEmpty()) {
      return MarketSweepMatchResult.rejected(normalizedRequestedQty, ErrorCode.ORD_NO_LIQUIDITY);
    }

    BigDecimal remainingQty = normalizedRequestedQty;
    BigDecimal grossNotional = ZERO;
    List<MarketSweepFill> fills = new ArrayList<>();

    for (CorebankOppositeBookQueryService.OppositeBookEntry candidate : oppositeBook) {
      if (remainingQty.signum() == 0) {
        break;
      }

      BigDecimal candidateRemainingQty = normalizePositive(candidate.remainingQty(), "candidate remainingQty is required");
      BigDecimal candidatePrice = normalizePositive(candidate.limitPrice(), "candidate limitPrice is required");
      BigDecimal fillQty = remainingQty.min(candidateRemainingQty);
      BigDecimal remainingMakerQty = candidateRemainingQty.subtract(fillQty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

      fills.add(new MarketSweepFill(
          candidate.orderId(),
          candidate.accountId(),
          candidate.clOrdId(),
          candidate.symbol(),
          candidate.side(),
          fillQty,
          candidatePrice,
          remainingMakerQty,
          candidate.priorityTime()
      ));

      remainingQty = remainingQty.subtract(fillQty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
      grossNotional = grossNotional.add(fillQty.multiply(candidatePrice)).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    if (fills.isEmpty()) {
      return MarketSweepMatchResult.rejected(normalizedRequestedQty, ErrorCode.ORD_NO_LIQUIDITY);
    }

    BigDecimal executedQty = normalizedRequestedQty.subtract(remainingQty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal executedPrice = grossNotional.divide(executedQty, MONEY_SCALE, RoundingMode.HALF_UP);
    String executionResult = remainingQty.signum() == 0 ? "FILLED" : "PARTIALLY_FILLED";
    return MarketSweepMatchResult.executed(fills, executionResult, executedQty, remainingQty, executedPrice);
  }

  private BigDecimal normalizePositive(BigDecimal value, String nullMessage) {
    if (value == null) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, nullMessage);
    }
    BigDecimal normalized = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    if (normalized.signum() <= 0) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, nullMessage);
    }
    return normalized;
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
