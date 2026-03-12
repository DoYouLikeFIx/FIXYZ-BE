package com.fix.channel.service;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.vo.OrderSessionAuthorizationDecision;
import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.channel.vo.OrderSessionQueryCommand;
import com.fix.channel.vo.OrderSessionResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderSessionService {

  private final OrderSessionRepository orderSessionRepository;
  private final SessionOwnershipValidator sessionOwnershipValidator;
  private final OrderSessionPersistenceService orderSessionPersistenceService;
  private final OrderSessionRateLimitService orderSessionRateLimitService;
  private final OrderSessionTtlStore orderSessionTtlStore;
  private final OrderSessionAuthorizationDecisionService orderSessionAuthorizationDecisionService;

  public OrderSessionResult createOrderSession(OrderSessionCreateCommand command) {
    OrderSession existingSession = orderSessionRepository.findByClOrdId(command.getClOrdId()).orElse(null);
    if (existingSession != null) {
      return replayExistingSession(existingSession, command);
    }

    orderSessionRateLimitService.enforceCreateRateLimit(command.getMemberId());
    OrderSessionAuthorizationDecision authorizationDecision =
        orderSessionAuthorizationDecisionService.evaluate(command);

    OrderSession savedSession;
    try {
      savedSession = orderSessionPersistenceService.createSession(command, authorizationDecision);
    } catch (DataIntegrityViolationException ex) {
      return resolveConcurrentReplay(command, ex);
    }

    try {
      orderSessionTtlStore.activate(savedSession.getOrderSessionId(), savedSession.getStatus().name());
    } catch (RuntimeException ex) {
      cleanupFailedActivation(savedSession.getOrderSessionId(), command.getMemberId(), ex);
      throw ex;
    }

    OrderSession activeSession = reloadSession(savedSession.getOrderSessionId());
    return buildResult(activeSession, requireRemainingSeconds(activeSession), true);
  }

  public OrderSessionResult getOrderSession(OrderSessionQueryCommand command) {
    OrderSession session = resolveSession(command);
    sessionOwnershipValidator.validateOwner(session, command.getMemberId());
    return buildResult(session, requireRemainingSeconds(session), false);
  }

  private OrderSession resolveSession(OrderSessionQueryCommand command) {
    if (command.getOrderSessionId() != null && !command.getOrderSessionId().isBlank()) {
      return orderSessionRepository.findByOrderSessionId(command.getOrderSessionId())
          .orElseThrow(this::orderSessionNotFound);
    }
    if (command.getClOrdId() != null && !command.getClOrdId().isBlank()) {
      return orderSessionRepository.findByClOrdId(command.getClOrdId())
          .orElseThrow(this::orderSessionNotFound);
    }
    throw orderSessionNotFound();
  }

  private Instant resolveExpiresAt(OrderSession session) {
    Instant createdAt = session.getCreatedAt();
    if (createdAt == null) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "order session creation timestamp missing");
    }
    return createdAt.plusSeconds(orderSessionTtlStore.ttlSeconds());
  }

  private OrderSessionResult buildResult(OrderSession session, Long remainingSeconds, boolean created) {
    return OrderSessionResult.of(
        session.getOrderSessionId(),
        session.getClOrdId(),
        session.getStatus().name(),
        resolveExpiresAt(session),
        remainingSeconds,
        session.isChallengeRequired(),
        session.getAuthorizationReason().name(),
        created
    );
  }

  private OrderSessionResult resolveConcurrentReplay(OrderSessionCreateCommand command, DataIntegrityViolationException ex) {
    OrderSession concurrentSession = orderSessionRepository.findByClOrdId(command.getClOrdId())
        .orElseThrow(() -> ex);
    try {
      sessionOwnershipValidator.validateOwner(concurrentSession, command.getMemberId());
      Long remainingSeconds = requireRemainingSeconds(concurrentSession);
      validateReplayPayload(concurrentSession, command);
      return buildResult(concurrentSession, remainingSeconds, false);
    } finally {
      safelyRefundCreateRateLimit(
          command.getMemberId(),
          concurrentSession.getOrderSessionId(),
          "duplicate create recovery",
          null
      );
    }
  }

  private OrderSessionResult replayExistingSession(OrderSession session, OrderSessionCreateCommand command) {
    sessionOwnershipValidator.validateOwner(session, command.getMemberId());
    Long remainingSeconds = requireRemainingSeconds(session);
    validateReplayPayload(session, command);
    return buildResult(session, remainingSeconds, false);
  }

  private void validateReplayPayload(OrderSession session, OrderSessionCreateCommand command) {
    if (!session.getOrderRef().equals(command.getOrderRef())) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "clOrdId replay payload mismatch");
    }
  }

  private Long expireAndReturnMissing(OrderSession session) {
    safelyClearTtl(session.getOrderSessionId(), null, "missing session reconciliation");
    orderSessionPersistenceService.expireSession(session.getOrderSessionId());
    throw orderSessionNotFound();
  }

  private Long requireRemainingSeconds(OrderSession session) {
    return orderSessionTtlStore.remainingSeconds(session.getOrderSessionId())
        .orElseGet(() -> expireAndReturnMissing(session));
  }

  private void cleanupFailedActivation(String orderSessionId, Long memberId, RuntimeException original) {
    safelyClearTtl(orderSessionId, original, "activation rollback");
    try {
      orderSessionPersistenceService.deleteCreatedSession(orderSessionId);
    } catch (RuntimeException cleanupFailure) {
      log.error("Failed to delete partially-created order session during activation rollback: orderSessionId={}",
          orderSessionId, cleanupFailure);
      original.addSuppressed(cleanupFailure);
    }
    safelyRefundCreateRateLimit(memberId, orderSessionId, "activation rollback", original);
  }

  private void safelyClearTtl(String orderSessionId, RuntimeException primaryFailure, String context) {
    try {
      orderSessionTtlStore.clear(orderSessionId);
    } catch (RuntimeException cleanupFailure) {
      log.warn("Failed to clear order session TTL during {}: orderSessionId={}", context, orderSessionId, cleanupFailure);
      if (primaryFailure != null) {
        primaryFailure.addSuppressed(cleanupFailure);
      }
    }
  }

  private void safelyRefundCreateRateLimit(
      Long memberId,
      String orderSessionId,
      String context,
      RuntimeException primaryFailure
  ) {
    try {
      orderSessionRateLimitService.refundCreateRateLimit(memberId);
    } catch (RuntimeException refundFailure) {
      log.warn("Failed to refund order session rate limit during {}: orderSessionId={}, memberId={}",
          context, orderSessionId, memberId, refundFailure);
      if (primaryFailure != null) {
        primaryFailure.addSuppressed(refundFailure);
      }
    }
  }

  private BusinessException orderSessionNotFound() {
    return new BusinessException(ErrorCode.ORDER_SESSION_NOT_FOUND, "Order session not found.");
  }

  private OrderSession reloadSession(String orderSessionId) {
    return orderSessionRepository.findByOrderSessionId(orderSessionId).orElseThrow(this::orderSessionNotFound);
  }
}
