package com.fix.channel.service;

import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.ManualRecoveryQueueEntry;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.channel.repository.ManualRecoveryQueueEntryRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.vo.OrderSessionCreateCommand;
import java.math.BigDecimal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSessionPersistenceService {

  private static final String ORDER_SESSION_TARGET_TYPE = "ORDER_SESSION";
  private static final List<OrderSessionStatus> EXPIRABLE_STATUSES =
      List.of(OrderSessionStatus.PENDING_NEW, OrderSessionStatus.AUTHED);

  private final ManualRecoveryQueueEntryRepository manualRecoveryQueueEntryRepository;
  private final OrderSessionRepository orderSessionRepository;
  private final AuditLogService auditLogService;
  private final OrderSessionMonitoringMetrics orderSessionMonitoringMetrics;
  private final Clock clock;

  @PersistenceContext
  private EntityManager entityManager;

  @Transactional
  OrderSession createSession(
      OrderSessionCreateCommand command,
      boolean challengeRequired,
      String authorizationReason,
      Instant expiresAt,
      String quoteSnapshotId,
      Instant quoteAsOf,
      FepQuoteSourceMode quoteSourceMode,
      BigDecimal preTradePrice
  ) {
    OrderSession savedSession = orderSessionRepository.saveAndFlush(OrderSession.initiated(
        command.getMemberId(),
        command.getAccountId(),
        command.getClOrdId(),
        command.replayFingerprint(),
        command.getSymbol(),
        command.getSide(),
        command.getOrderType(),
        command.getQty(),
        command.getPrice(),
        challengeRequired,
        authorizationReason,
        expiresAt,
        quoteSnapshotId,
        quoteAsOf,
        quoteSourceMode,
        preTradePrice
    ));
    entityManager.refresh(savedSession);

    recordOrderSessionAudit(savedSession, AuditAction.ORDER_SESSION_CREATE, "clOrdId=" + savedSession.getClOrdId());
    return savedSession;
  }

  @Transactional
  void expireSession(String orderSessionId) {
    orderSessionRepository.findByOrderSessionId(orderSessionId)
        .filter(OrderSession::hasActiveWindow)
        .ifPresent(session -> {
          session.expire();
          orderSessionRepository.flush();
          auditLogService.record(expiredAuditLog(session));
        });
  }

  @Transactional
  public List<String> expireOverdueSessionBatch(Instant referenceTime, int batchSize) {
    List<OrderSession> sessions = orderSessionRepository.findByStatusInAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
        EXPIRABLE_STATUSES,
        referenceTime,
        PageRequest.of(0, Math.max(1, batchSize))
    );
    sessions.forEach(OrderSession::expire);
    orderSessionRepository.flush();
    sessions.forEach(session -> auditLogService.record(expiredAuditLog(session)));
    return sessions.stream().map(OrderSession::getOrderSessionId).toList();
  }

  @Transactional
  void deleteCreatedSession(String orderSessionId) {
    orderSessionRepository.findByOrderSessionId(orderSessionId).ifPresent(session -> {
      auditLogService.record(AuditLog.ofOrderSession(
          session.getMemberId(),
          session.getId(),
          AuditAction.ORDER_SESSION_FAILED,
          ORDER_SESSION_TARGET_TYPE,
          session.getOrderSessionId(),
          "reason=activation_rollback, clOrdId=" + session.getClOrdId(),
          null,
          null,
          null
      ));
      orderSessionRepository.delete(session);
      orderSessionRepository.flush();
    });
  }

  @Transactional
  OrderSession markAuthorized(OrderSession session) {
    OrderSession managedSession = managedSession(session);
    managedSession.authorize();
    orderSessionRepository.flush();
    recordOrderSessionAudit(
        managedSession,
        AuditAction.ORDER_SESSION_OTP_VERIFIED,
        "clOrdId=" + managedSession.getClOrdId()
    );
    return managedSession;
  }

  @Transactional
  OrderSession extendSession(OrderSession session, Instant expiresAt) {
    OrderSession managedSession = managedSession(session);
    managedSession.extendExpiry(expiresAt);
    orderSessionRepository.flush();
    recordOrderSessionAudit(
        managedSession,
        AuditAction.ORDER_SESSION_EXTENDED,
        "clOrdId=" + managedSession.getClOrdId() + ", expiresAt=" + expiresAt
    );
    return managedSession;
  }

  @Transactional
  void restoreExpiry(OrderSession session, Instant expiresAt) {
    managedSession(session).extendExpiry(expiresAt);
    orderSessionRepository.flush();
  }

  @Transactional
  OrderSession markExecuting(OrderSession session) {
    OrderSession managedSession = managedSession(session);
    managedSession.startExecuting();
    orderSessionRepository.flush();
    return managedSession;
  }

  @Transactional
  OrderSession markRequerying(OrderSession session, String failureReason) {
    return markRequerying(session, failureReason, null, null, null, null, null, null, null);
  }

  @Transactional
  OrderSession markRequerying(
      OrderSession session,
      String failureReason,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Instant executedAt
  ) {
    OrderSession managedSession = managedSession(session);
    OrderSessionStatus previousStatus = managedSession.getStatus();
    managedSession.beginRequerying(
        failureReason,
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        externalSyncStatus,
        executedAt
    );
    orderSessionRepository.flush();
    recordRecoveryBacklogMutationIfNeeded(previousStatus, managedSession.getStatus());
    return managedSession;
  }

  @Transactional
  OrderSession markCompleted(
      OrderSession session,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Instant executedAt
  ) {
    OrderSession managedSession = managedSession(session);
    OrderSessionStatus previousStatus = managedSession.getStatus();
    managedSession.complete(
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        externalSyncStatus,
        executedAt
    );
    orderSessionRepository.flush();
    recordOrderSessionAudit(
        managedSession,
        AuditAction.ORDER_SESSION_EXECUTED,
        "clOrdId=" + managedSession.getClOrdId() + ", result=" + executionResult
    );
    orderSessionMonitoringMetrics.recordExecutionCompleted(managedSession);
    recordRecoveryBacklogMutationIfNeeded(previousStatus, managedSession.getStatus());
    return managedSession;
  }

  @Transactional
  OrderSession markCanceled(
      OrderSession session,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Instant executedAt,
      Instant canceledAt
  ) {
    OrderSession managedSession = managedSession(session);
    OrderSessionStatus previousStatus = managedSession.getStatus();
    managedSession.cancel(
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        externalSyncStatus,
        executedAt,
        canceledAt
    );
    orderSessionRepository.flush();
    recordOrderSessionAudit(
        managedSession,
        AuditAction.ORDER_SESSION_CANCELED,
        "clOrdId=" + managedSession.getClOrdId() + ", result=" + executionResult
    );
    recordRecoveryBacklogMutationIfNeeded(previousStatus, managedSession.getStatus());
    return managedSession;
  }

  @Transactional
  OrderSession markEscalated(OrderSession session, String failureReason) {
    OrderSession managedSession = managedSession(session);
    OrderSessionStatus previousStatus = managedSession.getStatus();
    managedSession.escalate(failureReason);
    orderSessionRepository.flush();
    recordOrderSessionAudit(
        managedSession,
        AuditAction.ORDER_SESSION_ESCALATED,
        "clOrdId=" + managedSession.getClOrdId() + ", reason=" + failureReason
    );
    recordRecoveryBacklogMutationIfNeeded(previousStatus, managedSession.getStatus());
    return managedSession;
  }

  @Transactional
  OrderSession markEscalated(
      OrderSession session,
      String failureReason,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Instant executedAt
  ) {
    OrderSession managedSession = managedSession(session);
    OrderSessionStatus previousStatus = managedSession.getStatus();
    managedSession.escalate(
        failureReason,
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        externalSyncStatus,
        executedAt
    );
    orderSessionRepository.flush();
    recordOrderSessionAudit(
        managedSession,
        AuditAction.ORDER_SESSION_ESCALATED,
        "clOrdId=" + managedSession.getClOrdId()
            + ", reason=" + failureReason
            + ", externalSyncStatus=" + externalSyncStatus
    );
    recordRecoveryBacklogMutationIfNeeded(previousStatus, managedSession.getStatus());
    return managedSession;
  }

  @Transactional
  OrderSession markEscalatedAndEnqueueManualRecovery(
      OrderSession session,
      String failureReason,
      String executionResult,
      BigDecimal executedQty,
      BigDecimal leavesQty,
      BigDecimal executedPrice,
      String externalOrderId,
      String externalSyncStatus,
      Instant executedAt,
      int attemptCount
  ) {
    OrderSession managedSession = managedSession(session);
    OrderSessionStatus previousStatus = managedSession.getStatus();
    managedSession.escalate(
        failureReason,
        executionResult,
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        externalSyncStatus,
        executedAt
    );
    Instant now = clock.instant();
    upsertManualRecoveryQueueEntry(
        managedSession.getOrderSessionId(),
        managedSession.getClOrdId(),
        attemptCount,
        failureReason,
        now
    );
    entityManager.flush();
    recordOrderSessionAudit(
        managedSession,
        AuditAction.ORDER_SESSION_ESCALATED,
        "clOrdId=" + managedSession.getClOrdId()
            + ", reason=" + failureReason
            + ", externalSyncStatus=" + externalSyncStatus
    );
    recordRecoveryBacklogMutationIfNeeded(previousStatus, managedSession.getStatus());
    return managedSession;
  }

  @Transactional
  OrderSession markFailed(OrderSession session, String failureReason) {
    OrderSession managedSession = managedSession(session);
    OrderSessionStatus previousStatus = managedSession.getStatus();
    managedSession.fail(failureReason);
    orderSessionRepository.flush();
    recordOrderSessionAudit(
        managedSession,
        AuditAction.ORDER_SESSION_FAILED,
        "clOrdId=" + managedSession.getClOrdId() + ", reason=" + failureReason
    );
    recordRecoveryBacklogMutationIfNeeded(previousStatus, managedSession.getStatus());
    return managedSession;
  }

  @Transactional
  OrderSession reconcileExternalLinkage(
      OrderSession session,
      String externalOrderId,
      String externalSyncStatus
  ) {
    OrderSession managedSession = managedSession(session);
    OrderSessionStatus previousStatus = managedSession.getStatus();
    managedSession.reconcileExternalLinkage(externalOrderId, externalSyncStatus);
    orderSessionRepository.flush();
    recordRecoveryBacklogMutationIfNeeded(previousStatus, managedSession.getStatus());
    return managedSession;
  }

  @Transactional(readOnly = true)
  List<OrderSession> findTimedOutExecutingSessions(Instant cutoffTime, int batchSize) {
    int effectiveBatchSize = Math.max(1, batchSize);
    List<OrderSession> sessions = new ArrayList<>();
    sessions.addAll(orderSessionRepository.findByStatusAndExecutingStartedAtLessThanEqualOrderByExecutingStartedAtAsc(
        OrderSessionStatus.EXECUTING,
        cutoffTime,
        PageRequest.of(0, effectiveBatchSize)
    ));
    if (sessions.size() >= effectiveBatchSize) {
      return sessions;
    }

    int remaining = effectiveBatchSize - sessions.size();
    sessions.addAll(orderSessionRepository.findByStatusAndExecutingStartedAtIsNullAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
        OrderSessionStatus.EXECUTING,
        cutoffTime,
        PageRequest.of(0, remaining)
    ));
    return sessions;
  }

  @Transactional(readOnly = true)
  List<OrderSession> findRequeryingSessionsAfter(
      Instant eligibleAt,
      Instant updatedAtCursor,
      String orderSessionIdCursor,
      int batchSize
  ) {
    PageRequest pageRequest = PageRequest.of(0, Math.max(1, batchSize));
    if (updatedAtCursor == null || orderSessionIdCursor == null || orderSessionIdCursor.isBlank()) {
      return orderSessionRepository.findEligibleByStatusOrderByUpdatedAtAscOrderSessionIdAsc(
          OrderSessionStatus.REQUERYING,
          eligibleAt,
          pageRequest
      );
    }
    return orderSessionRepository.findByStatusAfterUpdatedAtCursorOrderByUpdatedAtAscOrderSessionIdAsc(
        OrderSessionStatus.REQUERYING,
        eligibleAt,
        updatedAtCursor,
        orderSessionIdCursor,
        pageRequest
    );
  }

  private void upsertManualRecoveryQueueEntry(
      String orderSessionId,
      String clOrdId,
      int attemptCount,
      String reason,
      Instant now
  ) {
    ManualRecoveryQueueEntry queueEntry = manualRecoveryQueueEntryRepository.findByOrderSessionId(orderSessionId)
        .orElse(null);
    if (queueEntry == null) {
      manualRecoveryQueueEntryRepository.save(
          ManualRecoveryQueueEntry.pending(orderSessionId, clOrdId, attemptCount, reason, now)
      );
      return;
    }
    int updated = queueEntry.getResolvedAt() == null
        ? manualRecoveryQueueEntryRepository.refreshIfPending(
            queueEntry.getId(),
            queueEntry.getEnqueuedAt(),
            attemptCount,
            reason,
            now
        )
        : manualRecoveryQueueEntryRepository.refreshIfResolved(
            queueEntry.getId(),
            queueEntry.getEnqueuedAt(),
            queueEntry.getResolvedAt(),
            attemptCount,
            reason,
            now
        );
    if (updated == 0) {
      log.warn(
          "Manual recovery queue entry re-enqueue skipped because state changed concurrently: sessionId={}, clOrdId={}, enqueuedAt={}, resolvedAt={}",
          orderSessionId,
          clOrdId,
          queueEntry.getEnqueuedAt(),
          queueEntry.getResolvedAt()
      );
    }
  }

  private OrderSession managedSession(OrderSession session) {
    if (session == null) {
      throw new IllegalArgumentException("order session is required");
    }
    if (entityManager.contains(session)) {
      return session;
    }
    if (session.getId() != null) {
      return orderSessionRepository.findById(session.getId())
          .orElseThrow(() -> new IllegalStateException("order session not found: " + session.getId()));
    }
    if (session.getOrderSessionId() != null) {
      return orderSessionRepository.findByOrderSessionId(session.getOrderSessionId())
          .orElseThrow(() -> new IllegalStateException("order session not found: " + session.getOrderSessionId()));
    }
    throw new IllegalStateException("order session reference is missing persistent identity");
  }

  private AuditLog expiredAuditLog(OrderSession session) {
    return AuditLog.ofOrderSession(
        session.getMemberId(),
        session.getId(),
        AuditAction.ORDER_SESSION_EXPIRED,
        ORDER_SESSION_TARGET_TYPE,
        session.getOrderSessionId(),
        "clOrdId=" + session.getClOrdId() + ", expiresAt=" + session.getExpiresAt(),
        null,
        null,
        null
    );
  }

  private void recordOrderSessionAudit(OrderSession session, AuditAction action, String detail) {
    auditLogService.record(AuditLog.ofOrderSession(
        session.getMemberId(),
        session.getId(),
        action,
        ORDER_SESSION_TARGET_TYPE,
        session.getOrderSessionId(),
        detail,
        null,
        null,
        null
    ));
  }

  private void recordRecoveryBacklogMutationIfNeeded(
      OrderSessionStatus previousStatus,
      OrderSessionStatus nextStatus
  ) {
    if (orderSessionMonitoringMetrics.isRecoveryBacklogStatus(previousStatus)
        || orderSessionMonitoringMetrics.isRecoveryBacklogStatus(nextStatus)) {
      orderSessionMonitoringMetrics.refreshRecoveryBacklogLastUpdated();
    }
  }
}
