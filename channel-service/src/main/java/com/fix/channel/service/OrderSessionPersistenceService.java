package com.fix.channel.service;

import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.AuditLogRepository;
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
  private final AuditLogRepository auditLogRepository;

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

    auditLogRepository.save(AuditLog.of(
        command.getMemberId(),
        AuditAction.ORDER_SESSION_CREATE,
        ORDER_SESSION_TARGET_TYPE,
        savedSession.getOrderSessionId(),
        "clOrdId=" + savedSession.getClOrdId()
    ));
    return savedSession;
  }

  @Transactional
  void expireSession(String orderSessionId) {
    orderSessionRepository.findByOrderSessionId(orderSessionId)
        .filter(OrderSession::hasActiveWindow)
        .ifPresent(session -> {
          session.expire();
          orderSessionRepository.flush();
          auditLogRepository.save(expiredAuditLog(session));
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
    sessions.forEach(session -> auditLogRepository.save(expiredAuditLog(session)));
    return sessions.stream().map(OrderSession::getOrderSessionId).toList();
  }

  @Transactional
  void deleteCreatedSession(String orderSessionId) {
    auditLogRepository.deleteByActionAndTargetTypeAndTargetId(
        AuditAction.ORDER_SESSION_CREATE.value(),
        ORDER_SESSION_TARGET_TYPE,
        orderSessionId
    );
    orderSessionRepository.deleteByOrderSessionId(orderSessionId);
  }

  @Transactional
  OrderSession markAuthorized(OrderSession session) {
    session.authorize();
    orderSessionRepository.flush();
    auditLogRepository.save(AuditLog.of(
        session.getMemberId(),
        AuditAction.ORDER_SESSION_OTP_VERIFIED,
        ORDER_SESSION_TARGET_TYPE,
        session.getOrderSessionId(),
        "clOrdId=" + session.getClOrdId()
    ));
    return session;
  }

  @Transactional
  OrderSession extendSession(OrderSession session, Instant expiresAt) {
    session.extendExpiry(expiresAt);
    orderSessionRepository.flush();
    auditLogRepository.save(AuditLog.of(
        session.getMemberId(),
        AuditAction.ORDER_SESSION_EXTENDED,
        ORDER_SESSION_TARGET_TYPE,
        session.getOrderSessionId(),
        "clOrdId=" + session.getClOrdId() + ", expiresAt=" + expiresAt
    ));
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
      Instant executedAt
  ) {
    session.complete(executionResult, executedQty, leavesQty, executedPrice, externalOrderId, executedAt);
    orderSessionRepository.flush();
    auditLogRepository.save(AuditLog.of(
        session.getMemberId(),
        AuditAction.ORDER_SESSION_EXECUTED,
        ORDER_SESSION_TARGET_TYPE,
        session.getOrderSessionId(),
        "clOrdId=" + session.getClOrdId() + ", result=" + executionResult
    ));
    return session;
  }

  @Transactional
  OrderSession markFailed(OrderSession session, String failureReason) {
    session.fail(failureReason);
    orderSessionRepository.flush();
    auditLogRepository.save(AuditLog.of(
        session.getMemberId(),
        AuditAction.ORDER_SESSION_FAILED,
        ORDER_SESSION_TARGET_TYPE,
        session.getOrderSessionId(),
        "clOrdId=" + session.getClOrdId() + ", reason=" + failureReason
    ));
    return session;
  }

  private AuditLog expiredAuditLog(OrderSession session) {
    return AuditLog.of(
        session.getMemberId(),
        AuditAction.ORDER_SESSION_EXPIRED,
        ORDER_SESSION_TARGET_TYPE,
        session.getOrderSessionId(),
        "clOrdId=" + session.getClOrdId() + ", expiresAt=" + session.getExpiresAt()
    );
  }
}
