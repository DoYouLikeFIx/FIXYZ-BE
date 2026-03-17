package com.fix.channel.service;

import org.springframework.stereotype.Service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.vo.OrderExecuteCommand;
import com.fix.channel.vo.OrderExecuteResult;
import com.fix.channel.vo.OrderSessionResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.web.CorrelationIdSupport;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderExecutionService {

  private static final String NOTIFICATION_CHANNEL_ORDER = "ORDER";
  private final CorebankClient corebankClient;
  private final OrderSessionService orderSessionService;
  private final OrderSessionExecutionLockService orderSessionExecutionLockService;
  private final ChannelScaffoldService channelScaffoldService;
  private static final String EXTERNAL_SYNC_CONFIRMED = "CONFIRMED";

  public OrderSessionResult execute(Long memberId, String orderSessionId) {
    OrderSession session = orderSessionService.requireOwnedSession(memberId, orderSessionId);
    if (session.getStatus() == OrderSessionStatus.EXECUTING) {
      throw new BusinessException(
          ErrorCode.ORDER_SESSION_EXECUTION_IN_PROGRESS,
          "order session execution is already in progress"
      );
    }
    if (session.getStatus() != OrderSessionStatus.AUTHED) {
      throw new BusinessException(
          ErrorCode.ORDER_SESSION_NOT_AUTHORIZED,
          "order session is not authorized for execution"
      );
    }
    orderSessionService.ensureActiveWindow(session);

    orderSessionExecutionLockService.acquire(orderSessionId);
    try {
      OrderSession executingSession = orderSessionService.beginExecution(session);
      OrderExecuteResult result;
      try {
        result = corebankClient.executeOrder(toCommand(executingSession), CorrelationIdSupport.currentOrGenerate());
      } catch (RuntimeException ex) {
        try {
          handleExecutionFailure(executingSession, ex);
        } catch (RuntimeException markFailedEx) {
          ex.addSuppressed(markFailedEx);
        }
        throw ex;
      }

      if (requiresEscalation(result)) {
        OrderSession escalatedSession = orderSessionService.markEscalated(
            executingSession,
            OrderSession.ESCALATED_MANUAL_REVIEW,
            result.getExecutionResult(),
            result.getExecutedQty(),
            result.getLeavesQty(),
            result.getExecutedPrice(),
            result.getExternalOrderId(),
            result.getExternalSyncStatus(),
            result.getExecutedAt()
        );
<<<<<<< FIX-73-Story-7.2-CH-Notification-Persistence-APIs
        persistTerminalNotification(escalatedSession, "ESCALATED");
        return orderSessionService.toResult(escalatedSession, false);
=======
        return orderSessionService.toResult(escalatedSession, false, result.isIdempotent());
>>>>>>> main
      }

      OrderSession completedSession = orderSessionService.completeExecution(
          executingSession,
          result.getExecutionResult(),
          result.getExecutedQty(),
          result.getLeavesQty(),
          result.getExecutedPrice(),
          result.getExternalOrderId(),
          result.getExternalSyncStatus(),
          result.getExecutedAt()
      );
<<<<<<< FIX-73-Story-7.2-CH-Notification-Persistence-APIs
        persistTerminalNotification(completedSession, "COMPLETED");
      return orderSessionService.toResult(completedSession, false);
=======
      return orderSessionService.toResult(completedSession, false, result.isIdempotent());
>>>>>>> main
    } finally {
      orderSessionExecutionLockService.release(orderSessionId);
    }
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

  private String executionFailureReason(RuntimeException exception) {
    if (exception == null) {
      return "EXECUTION_FAILED";
    }
    if (exception instanceof BusinessException businessException) {
      return businessException.getErrorCode().code();
    }
    String simpleName = exception.getClass().getSimpleName();
    if (simpleName != null && !simpleName.isBlank()) {
      return simpleName;
    }
    return "EXECUTION_FAILED";
  }

  private void handleExecutionFailure(OrderSession session, RuntimeException exception) {
    if (requiresEscalation(exception)) {
      OrderSession escalatedSession = orderSessionService.markEscalated(session, OrderSession.ESCALATED_MANUAL_REVIEW);
      persistTerminalNotification(escalatedSession, "ESCALATED");
      return;
    }
    OrderSession failedSession = orderSessionService.markFailed(session, executionFailureReason(exception));
    persistTerminalNotification(failedSession, "FAILED");
  }

  private void persistTerminalNotification(OrderSession session, String terminalStatus) {
    String message = "orderSessionId=" + session.getOrderSessionId() + " status=" + terminalStatus;
    channelScaffoldService.bootstrapNotification(session.getMemberId(), NOTIFICATION_CHANNEL_ORDER, message);
  }

  private boolean requiresEscalation(OrderExecuteResult result) {
    String externalSyncStatus = result.getExternalSyncStatus();
    return externalSyncStatus != null && !EXTERNAL_SYNC_CONFIRMED.equalsIgnoreCase(externalSyncStatus);
  }

  private boolean requiresEscalation(RuntimeException exception) {
    if (!(exception instanceof BusinessException businessException)) {
      return false;
    }
    return switch (businessException.getErrorCode()) {
      case CHANNEL_ROUTE_NOT_FOUND,
           CORE_CONCURRENCY_CONFLICT,
           FEP_GATEWAY_UNAVAILABLE,
           FEP_GATEWAY_TIMEOUT,
           FEP_ORDER_REJECTED,
           FEP_UNKNOWN_EXTERNAL -> true;
      default -> false;
    };
  }
}
