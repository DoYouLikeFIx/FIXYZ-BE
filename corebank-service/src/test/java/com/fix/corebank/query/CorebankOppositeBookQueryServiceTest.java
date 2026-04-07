package com.fix.corebank.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.service.CorebankOppositeBookQueryService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:core_querydsl_book;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "corebank.order.optimized-book-selection-enabled=true",
    "corebank.order.book-selection-batch-size=8"
})
class CorebankOppositeBookQueryServiceTest {

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private CorebankOppositeBookQueryService oppositeBookQueryService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearOrders() {
    orderRepository.deleteAll();
  }

  @Test
  void shouldReturnSellBookInStrictPriceTimeOrderForBuyAggressor() {
    Instant tieTimestamp = Instant.parse("2026-03-01T09:00:00Z");

    Order first = persistRestingOrder(
        "market-sell-001",
        "SWEEP-SELL",
        101L,
        "SELL",
        "NEW",
        new BigDecimal("3.0000"),
        new BigDecimal("70000.0000"),
        tieTimestamp
    );
    Order second = persistRestingOrder(
        "market-sell-002",
        "SWEEP-SELL",
        102L,
        "SELL",
        "NEW",
        new BigDecimal("4.0000"),
        new BigDecimal("70000.0000"),
        tieTimestamp
    );
    Order partial = persistPartiallyFilledOrder(
        "market-sell-003",
        "SWEEP-SELL",
        103L,
        "SELL",
        new BigDecimal("5.0000"),
        new BigDecimal("70050.0000"),
        new BigDecimal("2.0000"),
        new BigDecimal("3.0000"),
        Instant.parse("2026-03-01T09:01:00Z")
    );
    Order last = persistRestingOrder(
        "market-sell-004",
        "SWEEP-SELL",
        104L,
        "SELL",
        "NEW",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000"),
        Instant.parse("2026-03-01T09:02:00Z")
    );

    persistRestingOrder(
        "market-buy-excluded",
        "SWEEP-SELL",
        105L,
        "BUY",
        "NEW",
        new BigDecimal("8.0000"),
        new BigDecimal("70200.0000"),
        Instant.parse("2026-03-01T09:03:00Z")
    );
    persistRestingOrder(
        "market-sell-canceled",
        "SWEEP-SELL",
        106L,
        "SELL",
        "CANCELED",
        new BigDecimal("8.0000"),
        new BigDecimal("69900.0000"),
        Instant.parse("2026-03-01T08:59:00Z")
    );
    persistRestingOrder(
        "market-sell-accepted-excluded",
        "SWEEP-SELL",
        108L,
        "SELL",
        "ACCEPTED",
        new BigDecimal("5.0000"),
        new BigDecimal("69900.0000"),
        Instant.parse("2026-03-01T08:57:00Z")
    );
    persistRestingOrder(
        "market-sell-pending-excluded",
        "SWEEP-SELL",
        109L,
        "SELL",
        "PENDING_NEW",
        new BigDecimal("5.0000"),
        new BigDecimal("69950.0000"),
        Instant.parse("2026-03-01T08:56:00Z")
    );
    persistRestingMarketOrder(
        "market-sell-resting-market",
        "SWEEP-SELL",
        107L,
        "SELL",
        new BigDecimal("2.0000"),
        new BigDecimal("72050.0000"),
        Instant.parse("2026-03-01T08:58:00Z")
    );

    List<CorebankOppositeBookQueryService.OppositeBookEntry> result =
        oppositeBookQueryService.findPreviewCandidates("SWEEP-SELL", "BUY");

    assertThat(result).extracting(CorebankOppositeBookQueryService.OppositeBookEntry::orderId)
        .containsExactly(first.getId(), second.getId(), partial.getId(), last.getId());
    assertThat(result).extracting(CorebankOppositeBookQueryService.OppositeBookEntry::clOrdId)
        .containsExactly("market-sell-001", "market-sell-002", "market-sell-003", "market-sell-004");
    assertThat(result).extracting(CorebankOppositeBookQueryService.OppositeBookEntry::limitPrice)
        .containsExactly(
            new BigDecimal("70000.0000"),
            new BigDecimal("70000.0000"),
            new BigDecimal("70050.0000"),
            new BigDecimal("70100.0000")
        );
    assertThat(result).extracting(CorebankOppositeBookQueryService.OppositeBookEntry::remainingQty)
        .containsExactly(
            new BigDecimal("3.0000"),
            new BigDecimal("4.0000"),
            new BigDecimal("3.0000"),
            new BigDecimal("2.0000")
        );
  }

  @Test
  void shouldReturnBuyBookInStrictPriceTimeOrderForSellAggressor() {
    Order tail = persistRestingOrder(
        "market-buy-001",
        "SWEEP-BUY",
        201L,
        "BUY",
        "NEW",
        new BigDecimal("3.0000"),
        new BigDecimal("70000.0000"),
        Instant.parse("2026-03-01T09:02:00Z")
    );
    Order best = persistRestingOrder(
        "market-buy-002",
        "SWEEP-BUY",
        202L,
        "BUY",
        "NEW",
        new BigDecimal("4.0000"),
        new BigDecimal("70200.0000"),
        Instant.parse("2026-03-01T09:00:00Z")
    );
    Order bestSecond = persistRestingOrder(
        "market-buy-003",
        "SWEEP-BUY",
        203L,
        "BUY",
        "PARTIALLY_FILLED",
        new BigDecimal("5.0000"),
        new BigDecimal("70200.0000"),
        Instant.parse("2026-03-01T09:01:00Z")
    );
    persistRemainingQuantity(bestSecond.getId(), new BigDecimal("2.0000"));
    Order lower = persistRestingOrder(
        "market-buy-004",
        "SWEEP-BUY",
        204L,
        "BUY",
        "NEW",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000"),
        Instant.parse("2026-03-01T09:03:00Z")
    );

    List<CorebankOppositeBookQueryService.OppositeBookEntry> result =
        oppositeBookQueryService.findPreviewCandidates("SWEEP-BUY", "SELL");

    assertThat(result).extracting(CorebankOppositeBookQueryService.OppositeBookEntry::orderId)
        .containsExactly(best.getId(), bestSecond.getId(), lower.getId(), tail.getId());
    assertThat(result).extracting(CorebankOppositeBookQueryService.OppositeBookEntry::limitPrice)
        .containsExactly(
            new BigDecimal("70200.0000"),
            new BigDecimal("70200.0000"),
            new BigDecimal("70100.0000"),
            new BigDecimal("70000.0000")
        );
    assertThat(result).extracting(CorebankOppositeBookQueryService.OppositeBookEntry::remainingQty)
        .containsExactly(
            new BigDecimal("4.0000"),
            new BigDecimal("2.0000"),
            new BigDecimal("2.0000"),
            new BigDecimal("3.0000")
        );
  }

  @Test
  void shouldExcludeExhaustedOrdersWhenLeavesQuantityIsZero() {
    Order active = persistRestingOrder(
        "market-sell-active",
        "SWEEP-LEGACY",
        301L,
        "SELL",
        "NEW",
        new BigDecimal("2.0000"),
        new BigDecimal("70000.0000"),
        Instant.parse("2026-03-01T09:00:00Z")
    );
    Order exhausted = persistRestingOrder(
        "market-sell-exhausted",
        "SWEEP-LEGACY",
        302L,
        "SELL",
        "PARTIALLY_FILLED",
        new BigDecimal("3.0000"),
        new BigDecimal("69950.0000"),
        Instant.parse("2026-03-01T08:59:00Z")
    );
    persistRemainingQuantity(exhausted.getId(), BigDecimal.ZERO);

    List<CorebankOppositeBookQueryService.OppositeBookEntry> result =
        oppositeBookQueryService.findPreviewCandidates("SWEEP-LEGACY", "BUY");

    assertThat(result).extracting(CorebankOppositeBookQueryService.OppositeBookEntry::orderId)
        .containsExactly(active.getId());
    assertThat(result).extracting(CorebankOppositeBookQueryService.OppositeBookEntry::clOrdId)
        .containsExactly("market-sell-active");
  }

  @Test
  void shouldRejectUnsupportedAggressorSide() {
    assertThatThrownBy(() -> oppositeBookQueryService.findPreviewCandidates("SWEEP-SELL", "HOLD"))
        .isInstanceOfSatisfying(BusinessException.class, ex -> {
          assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORD_INVALID_REQUEST);
          assertThat(ex.getMessage()).contains("side must be BUY or SELL");
        });
  }

  @Test
  void shouldApplySameStrictPriceTimeOrderingToExecutionLockQuery() {
    Instant tieTimestamp = Instant.parse("2026-03-01T09:00:00Z");
    Order best = persistRestingOrder(
        "lock-sell-001",
        "SWEEP-LOCK",
        401L,
        "SELL",
        "NEW",
        new BigDecimal("1.0000"),
        new BigDecimal("70000.0000"),
        tieTimestamp
    );
    Order next = persistRestingOrder(
        "lock-sell-002",
        "SWEEP-LOCK",
        402L,
        "SELL",
        "PARTIALLY_FILLED",
        new BigDecimal("1.5000"),
        new BigDecimal("70000.0000"),
        Instant.parse("2026-03-01T09:01:00Z")
    );
    persistRemainingQuantity(next.getId(), new BigDecimal("1.0000"));
    Order tail = persistRestingOrder(
        "lock-sell-003",
        "SWEEP-LOCK",
        403L,
        "SELL",
        "NEW",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000"),
        Instant.parse("2026-03-01T09:02:00Z")
    );

    List<Order> locked = oppositeBookQueryService.lockExecutionCandidates("SWEEP-LOCK", "BUY");

    assertThat(locked).extracting(Order::getId)
        .containsExactly(best.getId(), next.getId(), tail.getId());
    assertThat(locked).extracting(Order::getStatus)
        .containsExactly("NEW", "PARTIALLY_FILLED", "NEW");
  }

  @Test
  void shouldLockOnlyFirstChunkWhenEarlyLiquidityAlreadySatisfiesMarketOrder() {
    Instant baseTime = Instant.parse("2026-03-01T09:00:00Z");
    for (int index = 0; index < 100; index++) {
      persistRestingOrder(
          "chunk-market-sell-" + index,
          "SWEEP-CHUNK-MARKET",
          500L + index,
          "SELL",
          "NEW",
          new BigDecimal("1.0000"),
          new BigDecimal("70000.0000").add(BigDecimal.valueOf(index).setScale(4)),
          baseTime.plusSeconds(index)
      );
    }

    List<Order> locked = oppositeBookQueryService.lockExecutionCandidatesForSubmission(
        "SWEEP-CHUNK-MARKET",
        "BUY",
        "MARKET",
        new BigDecimal("3.0000"),
        null
    );

    assertThat(locked).hasSize(8);
    assertThat(locked).extracting(Order::getClOrdId)
        .containsExactly(
            "chunk-market-sell-0",
            "chunk-market-sell-1",
            "chunk-market-sell-2",
            "chunk-market-sell-3",
            "chunk-market-sell-4",
            "chunk-market-sell-5",
            "chunk-market-sell-6",
            "chunk-market-sell-7"
        );
  }

  @Test
  void shouldLockAdditionalChunksWhenRequestedQuantityExceedsSingleChunkCapacity() {
    Instant baseTime = Instant.parse("2026-03-01T09:00:00Z");
    for (int index = 0; index < 20; index++) {
      persistRestingOrder(
          "chunk-market-multi-" + index,
          "SWEEP-CHUNK-MULTI",
          700L + index,
          "SELL",
          "NEW",
          new BigDecimal("1.0000"),
          new BigDecimal("70000.0000").add(BigDecimal.valueOf(index).setScale(4)),
          baseTime.plusSeconds(index)
      );
    }

    List<Order> locked = oppositeBookQueryService.lockExecutionCandidatesForSubmission(
        "SWEEP-CHUNK-MULTI",
        "BUY",
        "MARKET",
        new BigDecimal("10.0000"),
        null
    );

    assertThat(locked).hasSize(16);
    assertThat(locked).extracting(Order::getClOrdId)
        .containsExactly(
            "chunk-market-multi-0",
            "chunk-market-multi-1",
            "chunk-market-multi-2",
            "chunk-market-multi-3",
            "chunk-market-multi-4",
            "chunk-market-multi-5",
            "chunk-market-multi-6",
            "chunk-market-multi-7",
            "chunk-market-multi-8",
            "chunk-market-multi-9",
            "chunk-market-multi-10",
            "chunk-market-multi-11",
            "chunk-market-multi-12",
            "chunk-market-multi-13",
            "chunk-market-multi-14",
            "chunk-market-multi-15"
        );
  }

  @Test
  void shouldValidateLimitInputsEvenWhenOptimizedSelectionIsDisabled() {
    ReflectionTestUtils.setField(oppositeBookQueryService, "optimizedBookSelectionEnabled", false);
    try {
      assertThatThrownBy(() -> oppositeBookQueryService.lockExecutionCandidatesForSubmission(
          "SWEEP-LIMIT-VALIDATION",
          "BUY",
          "LIMIT",
          new BigDecimal("1.0000"),
          null
      ))
          .isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ORD_INVALID_REQUEST);
            assertThat(ex.getMessage()).contains("limitPrice is required for LIMIT orders");
          });
    } finally {
      ReflectionTestUtils.setField(oppositeBookQueryService, "optimizedBookSelectionEnabled", true);
    }
  }

  private Order persistRestingOrder(
      String clOrdId,
      String symbol,
      Long accountId,
      String side,
      String status,
      BigDecimal quantity,
      BigDecimal price,
      Instant createdAt
  ) {
    Order order = Order.accepted(accountId, clOrdId, symbol, side, "LIMIT", quantity, price, null, null, null, null);
    order.updateStatus(status);
    Order saved = orderRepository.saveAndFlush(order);
    updateOrderTimestamps(saved.getId(), createdAt);
    return orderRepository.findById(saved.getId()).orElseThrow();
  }

  private Order persistPartiallyFilledOrder(
      String clOrdId,
      String symbol,
      Long accountId,
      String side,
      BigDecimal quantity,
      BigDecimal price,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      Instant createdAt
  ) {
    Order order = Order.accepted(accountId, clOrdId, symbol, side, "LIMIT", quantity, price, null, null, null, null);
    order.updateStatus("PARTIALLY_FILLED");
    order.updateExecutionSummary(
        "PARTIALLY_FILLED",
        executedQty,
        leavesQty,
        price,
        createdAt
    );
    Order saved = orderRepository.saveAndFlush(order);
    updateOrderTimestamps(saved.getId(), createdAt);
    return orderRepository.findById(saved.getId()).orElseThrow();
  }

  private void persistRemainingQuantity(Long orderId, BigDecimal leavesQty) {
    jdbcTemplate.update(
        "UPDATE orders SET leaves_qty = ? WHERE id = ?",
        leavesQty,
        orderId
    );
  }

  private void persistRestingMarketOrder(
      String clOrdId,
      String symbol,
      Long accountId,
      String side,
      BigDecimal quantity,
      BigDecimal preTradePrice,
      Instant createdAt
  ) {
    Order order = Order.accepted(
        accountId,
        clOrdId,
        symbol,
        side,
        "MARKET",
        quantity,
        null,
        preTradePrice,
        "qsnap-resting-market",
        createdAt,
        FepQuoteSourceMode.LIVE
    );
    Order saved = orderRepository.saveAndFlush(order);
    updateOrderTimestamps(saved.getId(), createdAt);
  }

  private void updateOrderTimestamps(Long orderId, Instant createdAt) {
    jdbcTemplate.update(
        "UPDATE orders SET created_at = ?, updated_at = ? WHERE id = ?",
        Timestamp.from(createdAt),
        Timestamp.from(createdAt),
        orderId
    );
  }
}
