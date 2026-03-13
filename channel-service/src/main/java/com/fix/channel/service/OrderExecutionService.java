package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.vo.OrderExecuteCommand;
import com.fix.channel.vo.OrderExecuteResult;
import com.fix.channel.vo.OrderSessionResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CorrelationIdSupport;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderExecutionService {

  private final CorebankClient corebankClient;
  private final OrderSessionService orderSessionService;
  private final OrderSessionPersistenceService orderSessionPersistenceService;
  private final OrderSessionExecutionLockService orderSessionExecutionLockService;
  private final Clock clock;

  public OrderSessionResult execute(Long memberId, String orderSessionId) {
    OrderSession session = orderSessionService.requireOwnedSession(memberId, orderSessionId);
    Long remainingSeconds = requireRemainingSeconds(session);
    if (session.getStatus() != OrderSessionStatus.AUTHED) {
      throw new BusinessException(
          ErrorCode.ORDER_SESSION_NOT_AUTHORIZED,
          "order session is not authorized for execution"
      );
    }

    orderSessionExecutionLockService.acquire(orderSessionId);
    orderSessionPersistenceService.markExecuting(session);

    OrderExecuteResult result = corebankClient.executeOrder(toCommand(session), CorrelationIdSupport.currentOrGenerate());
    OrderSession completedSession = orderSessionPersistenceService.markCompleted(
        session,
        result.getStatus(),
        result.getOrderQuantity(),
        java.math.BigDecimal.ZERO,
        session.getPrice(),
        result.getOrderId() == null ? null : String.valueOf(result.getOrderId()),
        Instant.now(clock)
    );
    return OrderSessionResult.of(
        completedSession.getOrderSessionId(),
        completedSession.getClOrdId(),
        completedSession.getStatus().name(),
        completedSession.isChallengeRequired(),
        completedSession.getAuthorizationReason(),
        completedSession.getAccountId(),
        completedSession.getSymbol(),
        completedSession.getSide(),
        completedSession.getOrderType(),
        completedSession.getQty(),
        completedSession.getPrice(),
        null,
        null,
        null,
        null,
        resolveExpiresAt(completedSession),
        remainingSeconds,
        completedSession.getExecutionResult(),
        completedSession.getExecutedQty(),
        completedSession.getLeavesQty(),
        completedSession.getExecutedPrice(),
        completedSession.getExternalOrderId(),
        completedSession.getFailureReason(),
        completedSession.getExecutedAt(),
        completedSession.getCanceledAt(),
        completedSession.getCreatedAt(),
        completedSession.getUpdatedAt(),
        false
    );
  }

  private OrderExecuteCommand toCommand(OrderSession session) {
    return OrderExecuteCommand.of(
        session.getAccountId(),
        session.getClOrdId(),
        session.getSymbol(),
        session.getSide(),
        session.getQty(),
        session.getPrice()
    );
  }

  private Long requireRemainingSeconds(OrderSession session) {
    if (session.getStatus() == OrderSessionStatus.EXPIRED || !resolveExpiresAt(session).isAfter(Instant.now(clock))) {
      orderSessionPersistenceService.expireSession(session.getOrderSessionId());
      throw orderSessionNotFound();
    }
    long remainingMillis = java.time.Duration.between(Instant.now(clock), session.getExpiresAt()).toMillis();
    return Math.max(1L, (remainingMillis + 999L) / 1000L);
  }

  private Instant resolveExpiresAt(OrderSession session) {
    Instant expiresAt = session.getExpiresAt();
    if (expiresAt == null) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "order session expiration timestamp missing");
    }
    return expiresAt;
  }

  private BusinessException orderSessionNotFound() {
    return new BusinessException(ErrorCode.ORDER_SESSION_NOT_FOUND, "Order session not found.");
  }
}
