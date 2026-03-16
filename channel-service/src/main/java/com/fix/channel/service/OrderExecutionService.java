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
  private final OrderSessionExecutionLockService orderSessionExecutionLockService;
  private final Clock clock;

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
          orderSessionService.markFailed(executingSession, executionFailureReason(ex));
        } catch (RuntimeException markFailedEx) {
          ex.addSuppressed(markFailedEx);
        }
        throw ex;
      }

      OrderSession completedSession = orderSessionService.completeExecution(
          executingSession,
          result.getStatus(),
          result.getOrderQuantity(),
          java.math.BigDecimal.ZERO,
          executingSession.getPrice(),
          result.getOrderId() == null ? null : String.valueOf(result.getOrderId()),
          Instant.now(clock)
      );
      return orderSessionService.toResult(completedSession, false);
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
    if (exception instanceof BusinessException businessException) {
      return businessException.getErrorCode().code();
    }
    String simpleName = exception.getClass().getSimpleName();
    if (simpleName != null && !simpleName.isBlank()) {
      return simpleName;
    }
    return "EXECUTION_FAILED";
  }
}
