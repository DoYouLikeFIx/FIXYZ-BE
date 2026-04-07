package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorebankOppositeBookQueryService {

  private static final int MONEY_SCALE = 4;
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  private static final List<String> MATCHABLE_BOOK_STATUSES = List.of("NEW", "PARTIALLY_FILLED");

  private final OrderRepository orderRepository;
  private final int executionSelectionBatchSize;

  @Value("${corebank.order.optimized-book-selection-enabled:false}")
  private boolean optimizedBookSelectionEnabled;

  public CorebankOppositeBookQueryService(
      OrderRepository orderRepository,
      @Value("${corebank.order.book-selection-batch-size:32}") int executionSelectionBatchSize
  ) {
    this.orderRepository = orderRepository;
    this.executionSelectionBatchSize = Math.max(1, executionSelectionBatchSize);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<OppositeBookEntry> findPreviewCandidates(String symbol, String aggressorSide) {
    return findPreviewRestingLimitOrders(symbol, aggressorSide).stream()
        .map(this::toEntry)
        .toList();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public List<Order> findPreviewRestingLimitOrders(String symbol, String aggressorSide) {
    String oppositeSide = resolveOppositeSide(aggressorSide);
    return orderRepository.findPreviewRestingLimitOrdersForSweep(symbol, oppositeSide, MATCHABLE_BOOK_STATUSES);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public List<Order> lockExecutionCandidates(String symbol, String aggressorSide) {
    String oppositeSide = resolveOppositeSide(aggressorSide);
    return orderRepository.lockExecutionRestingLimitOrdersForSweep(symbol, oppositeSide, MATCHABLE_BOOK_STATUSES);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public List<Order> lockExecutionCandidatesForSubmission(
      String symbol,
      String aggressorSide,
      String orderType,
      BigDecimal orderQty,
      BigDecimal limitPrice
  ) {
    String normalizedAggressorSide = normalizeSide(aggressorSide);
    String normalizedOrderType = normalizeOrderType(orderType);
    BigDecimal remainingQty = normalizePositive(orderQty, "orderQty is required");
    BigDecimal normalizedLimitPrice = normalizeLimitPriceIfRequired(normalizedOrderType, limitPrice);
    if (!optimizedBookSelectionEnabled) {
      return lockExecutionCandidates(symbol, normalizedAggressorSide);
    }
    String oppositeSide = resolveOppositeSide(normalizedAggressorSide);
    Cursor cursor = null;
    List<Order> lockedCandidates = new ArrayList<>();

    while (remainingQty.signum() > 0) {
      List<Order> chunk = orderRepository.lockExecutionRestingLimitOrdersForSweepChunk(
          symbol,
          oppositeSide,
          MATCHABLE_BOOK_STATUSES,
          cursor == null ? null : cursor.price(),
          cursor == null ? null : cursor.createdAt(),
          cursor == null ? null : cursor.orderId(),
          executionSelectionBatchSize
      );
      if (chunk.isEmpty()) {
        break;
      }

      lockedCandidates.addAll(chunk);
      if (shouldStopSelection(chunk, normalizedAggressorSide, normalizedOrderType, normalizedLimitPrice, remainingQty)) {
        break;
      }
      Order lastOrder = chunk.get(chunk.size() - 1);
      cursor = new Cursor(lastOrder.getOrderPrice(), lastOrder.getCreatedAt(), lastOrder.getId());
      remainingQty = remainingAfterChunk(chunk, remainingQty);
    }

    return lockedCandidates;
  }

  OppositeBookEntry toEntry(Order order) {
    return new OppositeBookEntry(
        order.getId(),
        order.getAccountId(),
        order.getClOrdId(),
        order.getSymbol(),
        order.getSide(),
        resolveRemainingQuantity(order),
        order.getOrderPrice(),
        order.getCreatedAt(),
        order.getStatus()
    );
  }

  private BigDecimal resolveRemainingQuantity(Order order) {
    BigDecimal remainingQuantity = order.getLeavesQty();
    if (remainingQuantity == null) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "order leavesQty must be present");
    }
    BigDecimal normalized = remainingQuantity.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    return normalized.signum() < 0 ? ZERO : normalized;
  }

  private boolean shouldStopSelection(
      List<Order> chunk,
      String aggressorSide,
      String orderType,
      BigDecimal limitPrice,
      BigDecimal remainingQty
  ) {
    BigDecimal batchRemainingQty = remainingQty;
    for (Order order : chunk) {
      OppositeBookEntry entry = toEntry(order);
      if ("LIMIT".equals(orderType) && !priceCrosses(aggressorSide, limitPrice, entry.limitPrice())) {
        return true;
      }
      batchRemainingQty = subtractRemaining(batchRemainingQty, entry.remainingQty());
      if (batchRemainingQty.signum() == 0) {
        return true;
      }
    }
    return false;
  }

  private BigDecimal remainingAfterChunk(List<Order> chunk, BigDecimal remainingQty) {
    BigDecimal nextRemaining = remainingQty;
    for (Order order : chunk) {
      nextRemaining = subtractRemaining(nextRemaining, resolveRemainingQuantity(order));
      if (nextRemaining.signum() == 0) {
        return ZERO;
      }
    }
    return nextRemaining;
  }

  private BigDecimal subtractRemaining(BigDecimal currentRemaining, BigDecimal candidateRemainingQty) {
    BigDecimal nextRemaining = normalizePositive(currentRemaining, "remainingQty is required")
        .subtract(normalizePositive(candidateRemainingQty, "candidate remainingQty is required"))
        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    return nextRemaining.signum() < 0 ? ZERO : nextRemaining;
  }

  private boolean priceCrosses(String aggressorSide, BigDecimal limitPrice, BigDecimal candidatePrice) {
    BigDecimal normalizedLimitPrice = normalizePositive(limitPrice, "limitPrice is required for LIMIT orders");
    BigDecimal normalizedCandidatePrice = normalizePositive(candidatePrice, "candidate limitPrice is required");
    if ("BUY".equals(aggressorSide)) {
      return normalizedLimitPrice.compareTo(normalizedCandidatePrice) >= 0;
    }
    return normalizedLimitPrice.compareTo(normalizedCandidatePrice) <= 0;
  }

  private String resolveOppositeSide(String aggressorSide) {
    String normalized = normalizeSide(aggressorSide);
    return "BUY".equals(normalized) ? "SELL" : "BUY";
  }

  private String normalizeSide(String side) {
    if (side == null || side.isBlank()) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "side is required");
    }
    String normalized = side.trim().toUpperCase(Locale.ROOT);
    if (!"BUY".equals(normalized) && !"SELL".equals(normalized)) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "side must be BUY or SELL");
    }
    return normalized;
  }

  private String normalizeOrderType(String orderType) {
    if (orderType == null || orderType.isBlank()) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "orderType is required");
    }
    String normalized = orderType.trim().toUpperCase(Locale.ROOT);
    if (!"LIMIT".equals(normalized) && !"MARKET".equals(normalized)) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "orderType must be LIMIT or MARKET");
    }
    return normalized;
  }

  private BigDecimal normalizeLimitPriceIfRequired(String orderType, BigDecimal limitPrice) {
    if ("LIMIT".equals(orderType)) {
      return normalizePositive(limitPrice, "limitPrice is required for LIMIT orders");
    }
    return null;
  }

  private BigDecimal normalizePositive(BigDecimal value, String message) {
    if (value == null) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, message);
    }
    BigDecimal normalized = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    if (normalized.signum() <= 0) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, message);
    }
    return normalized;
  }

  public record OppositeBookEntry(
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

  private record Cursor(
      BigDecimal price,
      Instant createdAt,
      Long orderId
  ) {
  }
}
