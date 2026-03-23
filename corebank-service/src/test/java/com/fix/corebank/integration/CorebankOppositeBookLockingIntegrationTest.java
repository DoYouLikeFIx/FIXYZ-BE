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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "internal.secret=test-secret",
    "corebank.order.book-lock-timeout-millis=0"
})
class CorebankOppositeBookLockingIntegrationTest extends CorebankContainersIntegrationTestBase {

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
  @Timeout(20)
  void shouldPreventConcurrentExecutionQueriesFromLockingSameLiquidityRows() throws Exception {
    Instant baseTime = Instant.parse("2026-03-01T09:00:00Z");
    Order lockedOrder = persistRestingOrder(
        "lock-row-001",
        "LOCK-SYMBOL",
        301L,
        "SELL",
        "NEW",
        new BigDecimal("3.0000"),
        new BigDecimal("70000.0000"),
        baseTime
    );

    CountDownLatch firstLockAcquired = new CountDownLatch(1);
    CountDownLatch releaseFirstLock = new CountDownLatch(1);
    TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    requiresNew.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

    ExecutorService executorService = Executors.newFixedThreadPool(2);
    try {
      Future<List<Long>> firstLock = executorService.submit(() -> requiresNew.execute(status -> {
        List<Order> locked = oppositeBookQueryService.lockExecutionCandidates("LOCK-SYMBOL", "BUY");
        firstLockAcquired.countDown();
        try {
          assertThat(releaseFirstLock.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("interrupted while holding opposite-book lock", ex);
        }
        return locked.stream().map(Order::getId).toList();
      }));

      Future<Throwable> secondLock = executorService.submit(() -> {
        assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
        try {
          requiresNew.execute(status -> {
            oppositeBookQueryService.lockExecutionCandidates("LOCK-SYMBOL", "BUY");
            return null;
          });
          return null;
        } catch (Throwable throwable) {
          return throwable;
        }
      });

      Throwable secondFailure = secondLock.get(10, TimeUnit.SECONDS);
      assertThat(secondFailure).isNotNull();
      assertThat(isLockFailure(secondFailure)).isTrue();

      releaseFirstLock.countDown();
      List<Long> lockedOrderIds = firstLock.get(10, TimeUnit.SECONDS);
      assertThat(lockedOrderIds).containsExactly(lockedOrder.getId());
    } finally {
      releaseFirstLock.countDown();
      executorService.shutdownNow();
      executorService.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private boolean isLockFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof CannotAcquireLockException
          || current instanceof PessimisticLockingFailureException
          || current instanceof jakarta.persistence.LockTimeoutException
          || current instanceof jakarta.persistence.PessimisticLockException) {
        return true;
      }
      String className = current.getClass().getName();
      if ("org.hibernate.exception.LockAcquisitionException".equals(className)
          || "org.springframework.dao.DeadlockLoserDataAccessException".equals(className)
          || "java.sql.SQLTransactionRollbackException".equals(className)
          || "com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException".equals(className)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
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
}
