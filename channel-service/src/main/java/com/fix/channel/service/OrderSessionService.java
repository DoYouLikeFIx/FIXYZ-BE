package com.fix.channel.service;

import com.fix.channel.entity.OrderSession;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.vo.OrderSessionCreateCommand;
import com.fix.channel.vo.OrderSessionQueryCommand;
import com.fix.channel.vo.OrderSessionResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
  private final Clock clock;

  public OrderSessionResult createOrderSession(OrderSessionCreateCommand command) {
    sessionOwnershipValidator.validateLinkedAccount(command.getMemberId(), command.getAccountId());

    OrderSession existingSession = orderSessionRepository.findByClOrdId(command.getClOrdId()).orElse(null);
    if (existingSession != null) {
      return replayExistingSession(existingSession, command);
    }

    orderSessionRateLimitService.enforceCreateRateLimit(command.getMemberId());

    OrderSession savedSession;
    Instant expiresAt = Instant.now(clock).plus(orderSessionTtlStore.ttl());
    try {
      savedSession = orderSessionPersistenceService.createPendingNewSession(command, expiresAt);
    } catch (DataIntegrityViolationException ex) {
      return resolveConcurrentReplay(command, ex);
    }

    try {
      orderSessionTtlStore.activate(savedSession.getOrderSessionId(), savedSession.getExpiresAt());
    } catch (RuntimeException ex) {
      cleanupFailedActivation(savedSession.getOrderSessionId(), command.getMemberId(), ex);
      throw ex;
    }

    return buildResult(savedSession, requireRemainingSeconds(savedSession), true);
  }

  public OrderSessionResult getOrderSession(OrderSessionQueryCommand command) {
    OrderSession session = resolveSession(command);
    sessionOwnershipValidator.validateOwner(session, command.getMemberId());
    return buildResult(session, requireRemainingSeconds(session), false);
  }

  private OrderSession resolveSession(OrderSessionQueryCommand command) {
    return orderSessionRepository.findByOrderSessionId(command.getOrderSessionId())
        .orElseThrow(this::orderSessionNotFound);
  }

  private Instant resolveExpiresAt(OrderSession session) {
    Instant expiresAt = session.getExpiresAt();
    if (expiresAt == null) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "order session expiration timestamp missing");
    }
    return expiresAt;
  }

  private OrderSessionResult buildResult(OrderSession session, Long remainingSeconds, boolean created) {
    return OrderSessionResult.of(
        session.getOrderSessionId(),
        session.getClOrdId(),
        session.getStatus().name(),
        session.getAccountId(),
        session.getSymbol(),
        session.getSide(),
        session.getOrderType(),
        session.getQty(),
        session.getPrice(),
        resolveExpiresAt(session),
        remainingSeconds,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        session.getCreatedAt(),
        session.getUpdatedAt(),
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
    if (!session.matchesReplayFingerprint(command.replayFingerprint())) {
      throw new BusinessException(ErrorCode.ORD_INVALID_REQUEST, "clOrdId replay payload mismatch");
    }
  }

  private Long expireAndReturnMissing(OrderSession session) {
    safelyClearTtl(session.getOrderSessionId(), null, "missing session reconciliation");
    orderSessionPersistenceService.expireSession(session.getOrderSessionId());
    throw orderSessionNotFound();
  }

  private Long requireRemainingSeconds(OrderSession session) {
    Instant expiresAt = resolveExpiresAt(session);
    if (!expiresAt.isAfter(Instant.now(clock))) {
      return expireAndReturnMissing(session);
    }
    if (!orderSessionTtlStore.isActive(session.getOrderSessionId())) {
      return expireAndReturnMissing(session);
    }
    return remainingSecondsUntil(expiresAt);
  }

  private Long remainingSecondsUntil(Instant expiresAt) {
    long remainingMillis = Duration.between(Instant.now(clock), expiresAt).toMillis();
    long roundedSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
    return Math.min(orderSessionTtlStore.ttl().toSeconds(), roundedSeconds);
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
}
