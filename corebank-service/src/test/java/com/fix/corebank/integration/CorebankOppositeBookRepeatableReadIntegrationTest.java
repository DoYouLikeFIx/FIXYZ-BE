package com.fix.corebank.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.service.CorebankOppositeBookQueryService;
import com.fix.corebank.support.CorebankContainersIntegrationTestBase;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret"
})
class CorebankOppositeBookRepeatableReadIntegrationTest extends CorebankContainersIntegrationTestBase {

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private CorebankOppositeBookQueryService oppositeBookQueryService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM executions");
    jdbcTemplate.update("DELETE FROM orders");
  }

  @Test
  void shouldKeepPreviewOrderingStableInsideRepeatableReadTransaction() {
    Instant baseTime = Instant.parse("2026-03-01T09:00:00Z");
    Order originalBest = persistRestingOrder(
        "preview-sell-001",
        "RR-PREVIEW",
        101L,
        "SELL",
        "NEW",
        new BigDecimal("2.0000"),
        new BigDecimal("70000.0000"),
        baseTime
    );
    Order originalTail = persistRestingOrder(
        "preview-sell-002",
        "RR-PREVIEW",
        102L,
        "SELL",
        "PARTIALLY_FILLED",
        new BigDecimal("3.0000"),
        new BigDecimal("70100.0000"),
        baseTime.plusSeconds(60)
    );
    persistRemainingQuantity(originalTail.getId(), new BigDecimal("1.0000"));

    TransactionTemplate repeatableRead = repeatableReadTemplate();
    TransactionTemplate requiresNew = requiresNewTemplate();

    List<Long> secondReadOrderIds = repeatableRead.execute(status -> {
      List<Long> firstReadOrderIds = oppositeBookQueryService.findPreviewCandidates("RR-PREVIEW", "BUY").stream()
          .map(CorebankOppositeBookQueryService.OppositeBookEntry::orderId)
          .toList();

      requiresNew.execute(writeStatus -> {
        persistRestingOrder(
            "preview-sell-late",
            "RR-PREVIEW",
            103L,
            "SELL",
            "NEW",
            new BigDecimal("1.0000"),
            new BigDecimal("69900.0000"),
            baseTime.minusSeconds(60)
        );
        return null;
      });

      List<Long> secondRead = oppositeBookQueryService.findPreviewCandidates("RR-PREVIEW", "BUY").stream()
          .map(CorebankOppositeBookQueryService.OppositeBookEntry::orderId)
          .toList();

      assertThat(firstReadOrderIds).containsExactly(originalBest.getId(), originalTail.getId());
      assertThat(secondRead).containsExactlyElementsOf(firstReadOrderIds);
      return secondRead;
    });

    assertThat(secondReadOrderIds).containsExactly(originalBest.getId(), originalTail.getId());
    assertThat(orderRepository.findAll()).hasSize(3);
  }

  @Test
  void shouldKeepExecutionCandidateSetStableInsideRepeatableReadTransaction() {
    Instant baseTime = Instant.parse("2026-03-01T10:00:00Z");
    Order best = persistRestingOrder(
        "lock-sell-001",
        "RR-LOCK",
        201L,
        "SELL",
        "NEW",
        new BigDecimal("2.0000"),
        new BigDecimal("70000.0000"),
        baseTime
    );
    Order next = persistRestingOrder(
        "lock-sell-002",
        "RR-LOCK",
        202L,
        "SELL",
        "NEW",
        new BigDecimal("1.5000"),
        new BigDecimal("70050.0000"),
        baseTime.plusSeconds(60)
    );

    TransactionTemplate repeatableRead = repeatableReadTemplate();
    TransactionTemplate requiresNew = requiresNewTemplate();

    List<Long> secondReadOrderIds = repeatableRead.execute(status -> {
      List<Long> firstReadOrderIds = oppositeBookQueryService.lockExecutionCandidates("RR-LOCK", "BUY").stream()
          .map(Order::getId)
          .toList();

      requiresNew.execute(writeStatus -> {
        persistRestingOrder(
            "lock-sell-late",
            "RR-LOCK",
            203L,
            "SELL",
            "NEW",
            new BigDecimal("1.0000"),
            new BigDecimal("69950.0000"),
            baseTime.minusSeconds(30)
        );
        return null;
      });

      List<Long> secondRead = oppositeBookQueryService.lockExecutionCandidates("RR-LOCK", "BUY").stream()
          .map(Order::getId)
          .toList();

      assertThat(firstReadOrderIds).containsExactly(best.getId(), next.getId());
      assertThat(secondRead).containsExactlyElementsOf(firstReadOrderIds);
      return secondRead;
    });

    assertThat(secondReadOrderIds).containsExactly(best.getId(), next.getId());
    assertThat(orderRepository.findAll()).hasSize(3);
  }

  private TransactionTemplate repeatableReadTemplate() {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return transactionTemplate;
  }

  private TransactionTemplate requiresNewTemplate() {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return transactionTemplate;
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
    jdbcTemplate.update(
        "UPDATE orders SET created_at = ?, updated_at = ? WHERE id = ?",
        Timestamp.from(createdAt),
        Timestamp.from(createdAt),
        saved.getId()
    );
    return orderRepository.findById(saved.getId()).orElseThrow();
  }

  private void persistRemainingQuantity(Long orderId, BigDecimal leavesQty) {
    jdbcTemplate.update("UPDATE orders SET leaves_qty = ? WHERE id = ?", leavesQty, orderId);
  }
}
