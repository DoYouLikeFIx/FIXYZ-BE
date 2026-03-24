package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.support.ChannelCorrelationIdSupport;
import com.fix.channel.vo.OrderRequeryResult;
import com.fix.common.error.BusinessException;
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
  private static final String NOTIFICATION_CHANNEL_ORDER = "ORDER";
  private static final String ORDER_SESSION_TARGET_TYPE = "ORDER_SESSION";
  private static final AuditAction RECOVERY_AUDIT_ACTION = AuditAction.ORDER_SESSION_RECOVERY_ATTEMPT;

  private final OrderSessionService orderSessionService;
  private final CorebankClient corebankClient;
  private final ChannelScaffoldService channelScaffoldService;
  private final AuditLogService auditLogService;
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
      ChannelScaffoldService channelScaffoldService,
      AuditLogService auditLogService,
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
    this.channelScaffoldService = channelScaffoldService;
    this.auditLogService = auditLogService;
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
    Instant eligibleAt = Instant.now(clock);
    Instant updatedAtCursor = null;
    String orderSessionIdCursor = null;
    while (true) {
      List<OrderSession> requeryingSessions =
          orderSessionService.findRequeryingSessionsAfter(eligibleAt, updatedAtCursor, orderSessionIdCursor, batchSize);
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
    String lockToken = recoveryLockService.tryAcquire(orderSessionId);
    if (lockToken == null) {
      return;
    }
    try {
      OrderSessionRecoveryAttemptStore.AttemptReservation reservation = attemptStore.reserveAttempt(orderSessionId);
      if (reservation == null) {
        return;
      }
      int attemptCount = reservation.attemptCount();
      String correlationId = ChannelCorrelationIdSupport.currentOrGenerate();
      requeryAttemptCounter.increment();
      OrderRequeryResult result;
      try {
        result = corebankClient.requeryOrder(
            session.getClOrdId(),
            attemptCount,
            correlationId
        );
      } catch (RuntimeException ex) {
        handleRequeryFailure(session, attemptCount, correlationId, ex);
        return;
      }

      if (isTerminalSuccess(result)) {
        OrderSession completedSession = orderSessionService.completeExecution(
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
        recordRecoveryAttemptAudit(
            completedSession,
            resolveEffectiveAttemptCount(result, attemptCount),
            "COMPLETED",
            result,
            null,
            correlationId
        );
        publishTerminalNotification(completedSession, "COMPLETED");
        return;
      }

      if (isTerminalCanceled(result)) {
        OrderSession canceledSession = orderSessionService.cancelExecution(
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
        recordRecoveryAttemptAudit(
            canceledSession,
            resolveEffectiveAttemptCount(result, attemptCount),
            "CANCELED",
            result,
            null,
            correlationId
        );
        publishTerminalNotification(canceledSession, "CANCELED");
        return;
      }

      if (shouldEscalate(result, attemptCount)) {
        OrderSession escalatedSession = orderSessionService.markEscalatedAndEnqueueManualRecovery(
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
        recordRecoveryAttemptAudit(
            escalatedSession,
            resolveEffectiveAttemptCount(result, attemptCount),
            "ESCALATED",
            result,
            null,
            correlationId
        );
        publishTerminalNotification(escalatedSession, "ESCALATED");
        return;
      }
      recordRecoveryAttemptAudit(
          session,
          resolveEffectiveAttemptCount(result, attemptCount),
          "RETRY_PENDING",
          result,
          null,
          correlationId
      );
    } catch (RuntimeException ex) {
      log.warn(
          "Order session recovery cycle failed for sessionId={}, clOrdId={}",
          orderSessionId,
          session.getClOrdId(),
          ex
      );
    } finally {
      recoveryLockService.release(orderSessionId, lockToken);
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

  private void handleRequeryFailure(
      OrderSession session,
      int attemptCount,
      String correlationId,
      RuntimeException ex
  ) {
    if (attemptCount >= maxRetryCount) {
      OrderSession escalatedSession = orderSessionService.markEscalatedAndEnqueueManualRecovery(
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
      recordRecoveryAttemptAudit(
          escalatedSession,
          attemptCount,
          "ESCALATED",
          null,
          recoveryAuditNote(ex),
          correlationId
      );
      publishTerminalNotification(escalatedSession, "ESCALATED");
      log.warn(
          "Order session recovery requery failed and escalated after retry exhaustion: sessionId={}, clOrdId={}, attemptCount={}",
          session.getOrderSessionId(),
          session.getClOrdId(),
          attemptCount,
          ex
      );
      return;
    }
    recordRecoveryAttemptAudit(
        session,
        attemptCount,
        "ERROR_RETRY_PENDING",
        null,
        recoveryAuditNote(ex),
        correlationId
    );
    log.warn(
        "Order session recovery requery failed: sessionId={}, clOrdId={}, attemptCount={}",
        session.getOrderSessionId(),
        session.getClOrdId(),
        attemptCount,
        ex
    );
  }

  private void publishTerminalNotification(OrderSession session, String terminalStatus) {
    channelScaffoldService.bootstrapNotification(
        session.getMemberId(),
        NOTIFICATION_CHANNEL_ORDER,
        "orderSessionId=" + session.getOrderSessionId() + " status=" + terminalStatus
    );
  }

  private void recordRecoveryAttemptAudit(
      OrderSession session,
      int attemptCount,
      String outcome,
      OrderRequeryResult result,
      String note,
      String correlationId
  ) {
    try {
      StringBuilder detail = new StringBuilder("clOrdId=")
          .append(session.getClOrdId())
          .append(", attemptCount=").append(attemptCount)
          .append(", outcome=").append(outcome);
      if (result != null) {
        detail.append(", recoveryStatus=").append(result.getStatus())
            .append(", externalSyncStatus=").append(result.getExternalSyncStatus())
            .append(", executionResult=").append(result.getExecutionResult());
      }
      if (note != null && !note.isBlank()) {
        detail.append(", note=").append(note);
      }
      auditLogService.record(AuditLog.ofOrderSession(
          session.getMemberId(),
          session.getId(),
          RECOVERY_AUDIT_ACTION,
          ORDER_SESSION_TARGET_TYPE,
          session.getOrderSessionId(),
          detail.toString(),
          null,
          null,
          correlationId
      ));
    } catch (RuntimeException ex) {
      log.warn(
          "Failed to record recovery attempt audit: sessionId={}, clOrdId={}, outcome={}, attemptCount={}",
          session.getOrderSessionId(),
          session.getClOrdId(),
          outcome,
          attemptCount,
          ex
      );
    }
  }

  private String recoveryAuditNote(RuntimeException ex) {
    if (ex == null) {
      return null;
    }
    if (ex instanceof BusinessException businessException) {
      return businessException.getClass().getSimpleName() + "[" + businessException.getErrorCode().name() + "]";
    }
    String simpleName = ex.getClass().getSimpleName();
    return simpleName == null || simpleName.isBlank() ? RuntimeException.class.getSimpleName() : simpleName;
  }

  private <T> T firstNonNull(T preferredValue, T fallbackValue) {
    return preferredValue != null ? preferredValue : fallbackValue;
  }
}
