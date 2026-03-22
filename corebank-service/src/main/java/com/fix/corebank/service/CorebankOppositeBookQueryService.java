package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CorebankOppositeBookQueryService {

  private static final int MONEY_SCALE = 4;
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  private static final List<String> MATCHABLE_BOOK_STATUSES = List.of("NEW", "PARTIALLY_FILLED");

  private final OrderRepository orderRepository;

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
      BigDecimal executedQuantity = order.getExecutedQty();
      if (executedQuantity == null) {
        executedQuantity = ZERO;
      }
      remainingQuantity = order.getOrderQty().subtract(executedQuantity);
    }
    BigDecimal normalized = remainingQuantity.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    return normalized.signum() < 0 ? ZERO : normalized;
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
}
