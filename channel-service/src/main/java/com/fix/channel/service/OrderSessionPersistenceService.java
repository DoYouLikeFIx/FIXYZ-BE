package com.fix.channel.service;

import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.AuditLogRepository;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.vo.OrderSessionCreateCommand;
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
  public OrderSession createPendingNewSession(OrderSessionCreateCommand command, Instant expiresAt) {
    OrderSession savedSession = orderSessionRepository.saveAndFlush(OrderSession.pendingNew(
        command.getMemberId(),
        command.getAccountId(),
        command.getClOrdId(),
        command.replayFingerprint(),
        command.getSymbol(),
        command.getSide(),
        command.getOrderType(),
        command.getQty(),
        command.getPrice(),
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
  public void expireSession(String orderSessionId) {
    orderSessionRepository.findByOrderSessionId(orderSessionId)
        .filter(session -> session.getStatus() != OrderSessionStatus.EXPIRED)
        .ifPresent(OrderSession::expire);
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
    return sessions.stream().map(OrderSession::getOrderSessionId).toList();
  }

  @Transactional
  public void deleteCreatedSession(String orderSessionId) {
    auditLogRepository.deleteByActionAndTargetTypeAndTargetId(
        AuditAction.ORDER_SESSION_CREATE.value(),
        ORDER_SESSION_TARGET_TYPE,
        orderSessionId
    );
    orderSessionRepository.deleteByOrderSessionId(orderSessionId);
  }
}
