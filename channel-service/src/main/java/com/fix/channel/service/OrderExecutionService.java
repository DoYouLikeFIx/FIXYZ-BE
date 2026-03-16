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
import java.math.BigDecimal;
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
          executionResult(result),
          executedQty(result),
          leavesQty(result),
          executedPrice(executingSession, result),
          externalOrderId(result),
          executedAt(result)
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

  private String executionResult(OrderExecuteResult result) {
    String executionResult = result.getExecutionResult();
    if (executionResult != null && !executionResult.isBlank()) {
      return executionResult;
    }
    return result.getStatus();
  }

  private BigDecimal executedQty(OrderExecuteResult result) {
    if (result.getExecutedQty() != null) {
      return result.getExecutedQty();
    }
    return result.getOrderQuantity();
  }

  private BigDecimal leavesQty(OrderExecuteResult result) {
    if (result.getLeavesQty() != null) {
      return result.getLeavesQty();
    }
    return BigDecimal.ZERO;
  }

  private BigDecimal executedPrice(OrderSession session, OrderExecuteResult result) {
    if (result.getExecutedPrice() != null) {
      return result.getExecutedPrice();
    }
    return session.getPrice();
  }

  private String externalOrderId(OrderExecuteResult result) {
    if (result.getExternalOrderId() != null && !result.getExternalOrderId().isBlank()) {
      return result.getExternalOrderId();
    }
    return result.getOrderId() == null ? null : String.valueOf(result.getOrderId());
  }

  private Instant executedAt(OrderExecuteResult result) {
    if (result.getExecutedAt() != null) {
      return result.getExecutedAt();
    }
    return Instant.now(clock);
  }
}
