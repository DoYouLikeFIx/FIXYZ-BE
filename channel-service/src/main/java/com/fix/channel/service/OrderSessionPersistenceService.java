package com.fix.channel.service;

import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.vo.OrderSessionCreateCommand;
import java.math.BigDecimal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderSessionPersistenceService {

  private static final String ORDER_SESSION_TARGET_TYPE = "ORDER_SESSION";
  private static final List<OrderSessionStatus> EXPIRABLE_STATUSES =
      List.of(OrderSessionStatus.PENDING_NEW, OrderSessionStatus.AUTHED);

  private final OrderSessionRepository orderSessionRepository;
  private final AuditLogService auditLogService;

  @PersistenceContext
  private EntityManager entityManager;

  @Transactional
  OrderSession createSession(
      OrderSessionCreateCommand command,
      boolean challengeRequired,
      String authorizationReason,
      Instant expiresAt
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
        expiresAt
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
    });
  }

  @Transactional
  OrderSession markAuthorized(OrderSession session) {
    session.authorize();
    orderSessionRepository.flush();
    recordOrderSessionAudit(session, AuditAction.ORDER_SESSION_OTP_VERIFIED, "clOrdId=" + session.getClOrdId());
    return session;
  }

  @Transactional
  OrderSession extendSession(OrderSession session, Instant expiresAt) {
    session.extendExpiry(expiresAt);
    orderSessionRepository.flush();
    recordOrderSessionAudit(
        session,
        AuditAction.ORDER_SESSION_EXTENDED,
        "clOrdId=" + session.getClOrdId() + ", expiresAt=" + expiresAt
    );
    return session;
  }

  @Transactional
  void restoreExpiry(OrderSession session, Instant expiresAt) {
    session.extendExpiry(expiresAt);
    orderSessionRepository.flush();
  }

  @Transactional
  OrderSession markExecuting(OrderSession session) {
    session.startExecuting();
    orderSessionRepository.flush();
    return session;
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
    session.complete(
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
        session,
        AuditAction.ORDER_SESSION_EXECUTED,
        "clOrdId=" + session.getClOrdId() + ", result=" + executionResult
    );
    return session;
  }

  @Transactional
  OrderSession markEscalated(OrderSession session, String failureReason) {
    session.escalate(failureReason);
    orderSessionRepository.flush();
    recordOrderSessionAudit(
        session,
        AuditAction.ORDER_SESSION_ESCALATED,
        "clOrdId=" + session.getClOrdId() + ", reason=" + failureReason
    );
    return session;
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
    session.escalate(
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
        session,
        AuditAction.ORDER_SESSION_ESCALATED,
        "clOrdId=" + session.getClOrdId()
            + ", reason=" + failureReason
            + ", externalSyncStatus=" + externalSyncStatus
    );
    return session;
  }

  @Transactional
  OrderSession markFailed(OrderSession session, String failureReason) {
    session.fail(failureReason);
    orderSessionRepository.flush();
    recordOrderSessionAudit(
        session,
        AuditAction.ORDER_SESSION_FAILED,
        "clOrdId=" + session.getClOrdId() + ", reason=" + failureReason
    );
    return session;
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
}
