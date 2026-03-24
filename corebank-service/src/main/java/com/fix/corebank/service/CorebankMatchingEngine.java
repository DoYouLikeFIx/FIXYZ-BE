package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepOrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class CorebankMatchingEngine {

  private static final int MONEY_SCALE = 4;
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

  public MatchResult match(MatchRequest request) {
    if (request == null) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "matchRequest is required");
    }

    String normalizedOrderType = normalizeOrderType(request.orderType());
    String normalizedSide = FepOrderType.MARKET.name().equals(normalizedOrderType)
        ? normalizeOptionalSide(request.side())
        : normalizeSide(request.side());
    BigDecimal requestedQty = normalizePositive(request.orderQty(), "orderQty is required");
    BigDecimal limitPrice = FepOrderType.LIMIT.name().equals(normalizedOrderType)
        ? normalizePositive(request.limitPrice(), "limitPrice is required for LIMIT orders")
        : null;

    List<MatchBookEntry> oppositeBook = request.oppositeBook() == null ? List.of() : request.oppositeBook();
    if (oppositeBook.isEmpty()) {
      return emptyBookResult(normalizedOrderType, requestedQty);
    }

    BigDecimal remainingQty = requestedQty;
    BigDecimal grossNotional = ZERO;
    List<MatchFill> fills = new ArrayList<>();

    for (MatchBookEntry candidate : oppositeBook) {
      if (remainingQty.signum() == 0) {
        break;
      }

      BigDecimal candidateRemainingQty = normalizePositive(candidate.remainingQty(), "candidate remainingQty is required");
      BigDecimal candidatePrice = normalizePositive(candidate.limitPrice(), "candidate limitPrice is required");
      if (FepOrderType.LIMIT.name().equals(normalizedOrderType)
          && !priceCrosses(normalizedSide, limitPrice, candidatePrice)) {
        break;
      }

      BigDecimal fillQty = remainingQty.min(candidateRemainingQty);
      BigDecimal remainingMakerQty = candidateRemainingQty.subtract(fillQty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
      fills.add(new MatchFill(
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
      return emptyBookResult(normalizedOrderType, requestedQty);
    }

    BigDecimal totalExecutedQty = requestedQty.subtract(remainingQty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal weightedAvgPrice = grossNotional.divide(totalExecutedQty, MONEY_SCALE, RoundingMode.HALF_UP);
    MatchDecision decision = remainingQty.signum() == 0 ? MatchDecision.FILLED : MatchDecision.PARTIALLY_FILLED;
    return MatchResult.executed(fills, decision, totalExecutedQty, remainingQty, weightedAvgPrice);
  }

  private MatchResult emptyBookResult(String orderType, BigDecimal requestedQty) {
    if (FepOrderType.MARKET.name().equals(orderType)) {
      return MatchResult.rejected(requestedQty, ErrorCode.ORD_NO_LIQUIDITY);
    }
    return MatchResult.resting(requestedQty);
  }

  private boolean priceCrosses(String side, BigDecimal limitPrice, BigDecimal makerPrice) {
    if ("BUY".equals(side)) {
      return makerPrice.compareTo(limitPrice) <= 0;
    }
    return makerPrice.compareTo(limitPrice) >= 0;
  }

  private String normalizeOrderType(String orderType) {
    if (orderType == null || orderType.isBlank()) {
      return FepOrderType.LIMIT.name();
    }
    String normalized = orderType.trim().toUpperCase(Locale.ROOT);
    if (!FepOrderType.LIMIT.name().equals(normalized) && !FepOrderType.MARKET.name().equals(normalized)) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "orderType must be LIMIT or MARKET");
    }
    return normalized;
  }

  private String normalizeSide(String side) {
    if (side == null || side.isBlank()) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "side is required");
    }
    return normalizeOptionalSide(side);
  }

  private String normalizeOptionalSide(String side) {
    if (side == null || side.isBlank()) {
      return null;
    }
    String normalized = side.trim().toUpperCase(Locale.ROOT);
    if (!"BUY".equals(normalized) && !"SELL".equals(normalized)) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "side must be BUY or SELL");
    }
    return normalized;
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

  public record MatchRequest(
      String orderType,
      String side,
      BigDecimal orderQty,
      BigDecimal limitPrice,
      List<MatchBookEntry> oppositeBook
  ) {
    public static MatchRequest market(BigDecimal orderQty, List<MatchBookEntry> oppositeBook) {
      return new MatchRequest(FepOrderType.MARKET.name(), null, orderQty, null, oppositeBook);
    }

    public static MatchRequest limit(
        String side,
        BigDecimal orderQty,
        BigDecimal limitPrice,
        List<MatchBookEntry> oppositeBook
    ) {
      return new MatchRequest(FepOrderType.LIMIT.name(), side, orderQty, limitPrice, oppositeBook);
    }
  }

  public record MatchBookEntry(
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
  }

  public record MatchFill(
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

  public enum MatchDecision {
    RESTING,
    FILLED,
    PARTIALLY_FILLED,
    REJECTED
  }

  public record MatchResult(
      MatchDecision decision,
      List<MatchFill> fills,
      BigDecimal totalExecutedQty,
      BigDecimal leavesQty,
      BigDecimal weightedAvgPrice,
      ErrorCode rejectCode
  ) {
    private static MatchResult resting(BigDecimal requestedQty) {
      return new MatchResult(MatchDecision.RESTING, List.of(), ZERO, requestedQty, null, null);
    }

    private static MatchResult rejected(BigDecimal requestedQty, ErrorCode rejectCode) {
      return new MatchResult(MatchDecision.REJECTED, List.of(), ZERO, requestedQty, null, rejectCode);
    }

    private static MatchResult executed(
        List<MatchFill> fills,
        MatchDecision decision,
        BigDecimal totalExecutedQty,
        BigDecimal leavesQty,
        BigDecimal weightedAvgPrice
    ) {
      return new MatchResult(List.copyOf(fills).isEmpty() ? MatchDecision.RESTING : decision, List.copyOf(fills), totalExecutedQty, leavesQty, weightedAvgPrice, null);
    }

    public boolean rejected() {
      return decision == MatchDecision.REJECTED;
    }

    public boolean resting() {
      return decision == MatchDecision.RESTING;
    }

    public String executionResult() {
      return switch (decision) {
        case FILLED, PARTIALLY_FILLED -> decision.name();
        default -> null;
      };
    }
  }
}
