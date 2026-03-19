package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.support.ChannelCorrelationIdSupport;
import com.fix.channel.vo.OrderRequeryResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrderSessionRecoveryService {

  private static final String EXECUTING_TIMEOUT_REASON = "EXECUTING_TIMEOUT";
  private static final String ESCALATED_REASON = OrderSession.ESCALATED_MANUAL_REVIEW;
  private static final String FILLED_STATUS = "FILLED";
  private static final String PARTIALLY_FILLED_STATUS = "PARTIALLY_FILLED";
  private static final String ACCEPTED_STATUS = "ACCEPTED";
  private static final String COMPLETED_STATUS = "COMPLETED";
  private static final String CANCELED_STATUS = "CANCELED";
  private static final String REJECTED_STATUS = "REJECTED";
  private static final String UNKNOWN_STATUS = "UNKNOWN";
  private static final String PENDING_STATUS = "PENDING";
  private static final String MALFORMED_STATUS = "MALFORMED";

  private final OrderSessionService orderSessionService;
  private final CorebankClient corebankClient;
  private final ManualRecoveryQueueService manualRecoveryQueueService;
  private final OrderSessionRecoveryLockService recoveryLockService;
  private final OrderSessionRecoveryAttemptStore attemptStore;
  private final Clock clock;
  private final Counter requeryAttemptCounter;
  private final Counter convergenceSuccessCounter;
  private final Counter convergenceEscalatedCounter;
  private final int batchSize;
  private final Duration executingTimeout;
  private final int maxRetryCount;

  public OrderSessionRecoveryService(
      OrderSessionService orderSessionService,
      CorebankClient corebankClient,
      ManualRecoveryQueueService manualRecoveryQueueService,
      OrderSessionRecoveryLockService recoveryLockService,
      OrderSessionRecoveryAttemptStore attemptStore,
      Clock clock,
      MeterRegistry meterRegistry,
      @Value("${order.session.recovery.batch-size:100}") int batchSize,
      @Value("${order.session.recovery.executing-timeout:30s}") Duration executingTimeout,
      @Value("${order.session.recovery.max-retry-count:5}") int maxRetryCount
  ) {
    this.orderSessionService = orderSessionService;
    this.corebankClient = corebankClient;
    this.manualRecoveryQueueService = manualRecoveryQueueService;
    this.recoveryLockService = recoveryLockService;
    this.attemptStore = attemptStore;
    this.clock = clock;
    this.batchSize = Math.max(1, batchSize);
    this.executingTimeout = executingTimeout.isNegative() ? Duration.ZERO : executingTimeout;
    this.maxRetryCount = Math.max(1, maxRetryCount);
    this.requeryAttemptCounter = Counter.builder("channel.order.recovery.requery.attempts")
        .description("Total UNKNOWN/EXECUTING requery attempts by recovery scheduler")
        .register(meterRegistry);
    this.convergenceSuccessCounter = Counter.builder("channel.order.recovery.convergence")
        .description("Recovered order sessions converged by recovery scheduler")
        .tag("outcome", "success")
        .register(meterRegistry);
    this.convergenceEscalatedCounter = Counter.builder("channel.order.recovery.convergence")
        .description("Recovered order sessions converged by recovery scheduler")
        .tag("outcome", "escalated")
        .register(meterRegistry);
  }

  public void runRecoveryCycle() {
    transitionTimedOutExecutingSessions();
    processRequeryingSessions();
    manualRecoveryQueueService.publishPendingEntries();
  }

  private void transitionTimedOutExecutingSessions() {
    Instant cutoff = Instant.now(clock).minus(executingTimeout);
    List<OrderSession> timedOutSessions = orderSessionService.findTimedOutExecutingSessions(cutoff, batchSize);
    for (OrderSession session : timedOutSessions) {
      try {
        orderSessionService.beginRequerying(session, EXECUTING_TIMEOUT_REASON);
      } catch (RuntimeException ex) {
        log.warn(
            "Failed to transition timed-out order session to REQUERYING: sessionId={}, clOrdId={}",
            session.getOrderSessionId(),
            session.getClOrdId(),
            ex
        );
      }
    }
  }

  private void processRequeryingSessions() {
    Instant updatedAtCursor = null;
    String orderSessionIdCursor = null;
    while (true) {
      List<OrderSession> requeryingSessions =
          orderSessionService.findRequeryingSessionsAfter(updatedAtCursor, orderSessionIdCursor, batchSize);
      if (requeryingSessions.isEmpty()) {
        return;
      }
      for (OrderSession session : requeryingSessions) {
        processSingleSession(session);
      }
      OrderSession lastSession = requeryingSessions.get(requeryingSessions.size() - 1);
      updatedAtCursor = lastSession.getUpdatedAt();
      orderSessionIdCursor = lastSession.getOrderSessionId();
      if (requeryingSessions.size() < batchSize) {
        return;
      }
    }
  }

  private void processSingleSession(OrderSession session) {
    String orderSessionId = session.getOrderSessionId();
    if (!recoveryLockService.tryAcquire(orderSessionId)) {
      return;
    }
    try {
      int attemptCount = attemptStore.nextAttempt(orderSessionId);
      requeryAttemptCounter.increment();
      OrderRequeryResult result;
      try {
        result = corebankClient.requeryOrder(
            session.getClOrdId(),
            attemptCount,
            ChannelCorrelationIdSupport.currentOrGenerate()
        );
      } catch (RuntimeException ex) {
        handleRequeryFailure(session, attemptCount, ex);
        return;
      }

      if (isTerminalSuccess(result)) {
        orderSessionService.completeExecution(
            session,
            firstNonNull(result.getExecutionResult(), session.getExecutionResult()),
            firstNonNull(result.getExecutedQty(), session.getExecutedQty()),
            firstNonNull(result.getLeavesQty(), session.getLeavesQty()),
            firstNonNull(result.getExecutedPrice(), session.getExecutedPrice()),
            firstNonNull(result.getExternalOrderId(), session.getExternalOrderId()),
            firstNonNull(result.getExternalSyncStatus(), session.getExternalSyncStatus()),
            firstNonNull(result.getExecutedAt(), session.getExecutedAt())
        );
        attemptStore.clear(orderSessionId);
        convergenceSuccessCounter.increment();
        return;
      }

      if (isTerminalCanceled(result)) {
        orderSessionService.cancelExecution(
            session,
            firstNonNull(result.getExecutionResult(), session.getExecutionResult()),
            firstNonNull(result.getExecutedQty(), session.getExecutedQty()),
            firstNonNull(result.getLeavesQty(), session.getLeavesQty()),
            firstNonNull(result.getExecutedPrice(), session.getExecutedPrice()),
            firstNonNull(result.getExternalOrderId(), session.getExternalOrderId()),
            firstNonNull(result.getExternalSyncStatus(), session.getExternalSyncStatus()),
            firstNonNull(result.getExecutedAt(), session.getExecutedAt()),
            firstNonNull(result.getCanceledAt(), session.getCanceledAt())
        );
        attemptStore.clear(orderSessionId);
        convergenceSuccessCounter.increment();
        return;
      }

      if (shouldEscalate(result, attemptCount)) {
        orderSessionService.markEscalatedAndEnqueueManualRecovery(
            session,
            escalationReason(result),
            firstNonNull(result.getExecutionResult(), session.getExecutionResult()),
            firstNonNull(result.getExecutedQty(), session.getExecutedQty()),
            firstNonNull(result.getLeavesQty(), session.getLeavesQty()),
            firstNonNull(result.getExecutedPrice(), session.getExecutedPrice()),
            firstNonNull(result.getExternalOrderId(), session.getExternalOrderId()),
            firstNonNull(result.getExternalSyncStatus(), session.getExternalSyncStatus()),
            firstNonNull(result.getExecutedAt(), session.getExecutedAt()),
            resolveEffectiveAttemptCount(result, attemptCount)
        );
        attemptStore.clear(orderSessionId);
        convergenceEscalatedCounter.increment();
      }
    } catch (RuntimeException ex) {
      log.warn(
          "Order session recovery cycle failed for sessionId={}, clOrdId={}",
          orderSessionId,
          session.getClOrdId(),
          ex
      );
    } finally {
      recoveryLockService.release(orderSessionId);
    }
  }

  private boolean isTerminalSuccess(OrderRequeryResult result) {
    String status = result.getStatus();
    return FILLED_STATUS.equalsIgnoreCase(status)
        || PARTIALLY_FILLED_STATUS.equalsIgnoreCase(status)
        || ACCEPTED_STATUS.equalsIgnoreCase(status)
        || COMPLETED_STATUS.equalsIgnoreCase(status);
  }

  private boolean isTerminalCanceled(OrderRequeryResult result) {
    return CANCELED_STATUS.equalsIgnoreCase(result.getStatus());
  }

  private boolean shouldEscalate(OrderRequeryResult result, int localAttemptCount) {
    return REJECTED_STATUS.equalsIgnoreCase(result.getStatus())
        || Boolean.TRUE.equals(result.getEscalationRequired())
        || hasReachedRetryLimit(result, localAttemptCount);
  }

  private boolean hasReachedRetryLimit(OrderRequeryResult result, int localAttemptCount) {
    if (!isRetryLimitStatus(result)) {
      return false;
    }
    return resolveEffectiveAttemptCount(result, localAttemptCount) >= resolveMaxRetryCount(result);
  }

  private boolean isRetryLimitStatus(OrderRequeryResult result) {
    String status = result.getStatus();
    return UNKNOWN_STATUS.equalsIgnoreCase(status)
        || PENDING_STATUS.equalsIgnoreCase(status)
        || MALFORMED_STATUS.equalsIgnoreCase(status);
  }

  private String escalationReason(OrderRequeryResult result) {
    return ESCALATED_REASON;
  }

  private int resolveEffectiveAttemptCount(OrderRequeryResult result, int localAttemptCount) {
    Integer reportedAttemptCount = result.getAttemptCount();
    if (reportedAttemptCount == null) {
      return localAttemptCount;
    }
    return Math.max(localAttemptCount, reportedAttemptCount);
  }

  private int resolveMaxRetryCount(OrderRequeryResult result) {
    Integer reportedMaxRetryCount = result.getMaxRetryCount();
    if (reportedMaxRetryCount == null || reportedMaxRetryCount < 1) {
      return maxRetryCount;
    }
    return reportedMaxRetryCount;
  }

  private void handleRequeryFailure(OrderSession session, int attemptCount, RuntimeException ex) {
    if (attemptCount >= maxRetryCount) {
      orderSessionService.markEscalatedAndEnqueueManualRecovery(
          session,
          ESCALATED_REASON,
          session.getExecutionResult(),
          session.getExecutedQty(),
          session.getLeavesQty(),
          session.getExecutedPrice(),
          session.getExternalOrderId(),
          session.getExternalSyncStatus(),
          session.getExecutedAt(),
          attemptCount
      );
      attemptStore.clear(session.getOrderSessionId());
      convergenceEscalatedCounter.increment();
      log.warn(
          "Order session recovery requery failed and escalated after retry exhaustion: sessionId={}, clOrdId={}, attemptCount={}",
          session.getOrderSessionId(),
          session.getClOrdId(),
          attemptCount,
          ex
      );
      return;
    }
    log.warn(
        "Order session recovery requery failed: sessionId={}, clOrdId={}, attemptCount={}",
        session.getOrderSessionId(),
        session.getClOrdId(),
        attemptCount,
        ex
    );
  }

  private <T> T firstNonNull(T preferredValue, T fallbackValue) {
    return preferredValue != null ? preferredValue : fallbackValue;
  }
}
