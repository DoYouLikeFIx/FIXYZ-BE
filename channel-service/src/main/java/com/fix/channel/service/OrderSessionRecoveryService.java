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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrderSessionRecoveryService {

  private static final String EXECUTING_TIMEOUT_REASON = "EXECUTING_TIMEOUT";
  private static final String ESCALATED_REASON = OrderSession.ESCALATED_MANUAL_REVIEW;
  private static final String FILLED_STATUS = "FILLED";
  private static final String ACCEPTED_STATUS = "ACCEPTED";
  private static final String COMPLETED_STATUS = "COMPLETED";

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

  public OrderSessionRecoveryService(
      OrderSessionService orderSessionService,
      CorebankClient corebankClient,
      ManualRecoveryQueueService manualRecoveryQueueService,
      OrderSessionRecoveryLockService recoveryLockService,
      OrderSessionRecoveryAttemptStore attemptStore,
      Clock clock,
      MeterRegistry meterRegistry,
      @Value("${order.session.recovery.batch-size:100}") int batchSize,
      @Value("${order.session.recovery.executing-timeout:30s}") Duration executingTimeout
  ) {
    this.orderSessionService = orderSessionService;
    this.corebankClient = corebankClient;
    this.manualRecoveryQueueService = manualRecoveryQueueService;
    this.recoveryLockService = recoveryLockService;
    this.attemptStore = attemptStore;
    this.clock = clock;
    this.batchSize = Math.max(1, batchSize);
    this.executingTimeout = executingTimeout.isNegative() ? Duration.ZERO : executingTimeout;
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
    Set<String> processedSessionIds = new HashSet<>();
    while (true) {
      List<OrderSession> requeryingSessions = orderSessionService.findRequeryingSessions(batchSize);
      if (requeryingSessions.isEmpty()) {
        return;
      }
      boolean processedAny = false;
      for (OrderSession session : requeryingSessions) {
        if (!processedSessionIds.add(session.getOrderSessionId())) {
          continue;
        }
        processedAny = true;
        processSingleSession(session);
      }
      if (requeryingSessions.size() < batchSize || !processedAny) {
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
      OrderRequeryResult result = corebankClient.requeryOrder(
          session.getClOrdId(),
          attemptCount,
          ChannelCorrelationIdSupport.currentOrGenerate()
      );

      if (isTerminalSuccess(result)) {
        orderSessionService.completeExecution(
            session,
            result.getExecutionResult(),
            result.getExecutedQty(),
            result.getLeavesQty(),
            result.getExecutedPrice(),
            result.getExternalOrderId(),
            result.getExternalSyncStatus(),
            result.getExecutedAt()
        );
        attemptStore.clear(orderSessionId);
        convergenceSuccessCounter.increment();
        return;
      }

      if (Boolean.TRUE.equals(result.getEscalationRequired())) {
        orderSessionService.markEscalated(
            session,
            escalationReason(result),
            result.getExecutionResult(),
            result.getExecutedQty(),
            result.getLeavesQty(),
            result.getExecutedPrice(),
            result.getExternalOrderId(),
            result.getExternalSyncStatus(),
            result.getExecutedAt()
        );
        manualRecoveryQueueService.enqueue(
            orderSessionId,
            session.getClOrdId(),
            attemptCount,
            escalationReason(result)
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
        || ACCEPTED_STATUS.equalsIgnoreCase(status)
        || COMPLETED_STATUS.equalsIgnoreCase(status);
  }

  private String escalationReason(OrderRequeryResult result) {
    return ESCALATED_REASON;
  }
}
