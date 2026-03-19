package com.fix.corebank.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepReplayPayload;
import com.fix.corebank.client.FepReplayResult;
import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.vo.InternalOrderReplayCommand;
import com.fix.corebank.vo.InternalOrderReplayResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorebankOrderReplayService {

  private static final int MONEY_SCALE = 4;
  private static final String FINAL_STATUS_COMPLETED = "COMPLETED";
  private static final String FINAL_STATUS_FAILED = "FAILED";
  private static final String FINAL_STATUS_CANCELED = "CANCELED";
  private static final String EXECUTION_RESULT_FILLED = "FILLED";
  private static final String EXECUTION_RESULT_PARTIAL_FILL = "PARTIAL_FILL";
  private static final String EXECUTION_RESULT_CANCELED = "CANCELED";
  private static final String EXECUTION_RESULT_PARTIAL_FILL_CANCEL = "PARTIAL_FILL_CANCEL";
  private static final String FAILURE_REASON_MANUAL_REJECT = "MANUAL_REJECT";

  private final OrderRepository orderRepository;
  private final FepClient fepClient;

  @Value("${corebank.manual-replay.max-attempts:2}")
  private int maxAttempts = 2;

  @Transactional
  public InternalOrderReplayResult replay(InternalOrderReplayCommand command) {
    Order order = orderRepository.findByClOrdIdForUpdate(command.getClOrdId())
        .orElseThrow(() -> new BusinessException(ErrorCode.CORE_RESOURCE_NOT_FOUND, "order not found"));

    if (!Order.EXTERNAL_SYNC_ESCALATED.equals(order.getExternalSyncStatus())) {
      throw new BusinessException(ErrorCode.INVALID_SESSION_STATUS, "replay target must be ESCALATED");
    }

    FepReplayResult replayResult = invokeReplay(command);
    FepOrderResult statusSnapshot = queryStatusAfterReplay(command.getClOrdId(), command.getCorrelationId());

    InternalOrderReplayResult resolved = switch (replayResult.finalStatus()) {
      case FINAL_STATUS_COMPLETED -> resolveCompleted(order, command, replayResult, statusSnapshot);
      case FINAL_STATUS_FAILED -> resolveRejected(order, replayResult, statusSnapshot);
      case FINAL_STATUS_CANCELED -> resolveCanceled(order, replayResult, statusSnapshot);
      default -> throw new BusinessException(ErrorCode.INTERNAL_ERROR, "unsupported replay final status");
    };

    orderRepository.flush();
    return resolved;
  }

  private FepReplayResult invokeReplay(InternalOrderReplayCommand command) {
    BusinessException lastRetriableFailure = null;
    int attempts = Math.max(1, maxAttempts);
    for (int attempt = 1; attempt <= attempts; attempt++) {
      try {
        return fepClient.replayOrder(
            new FepReplayPayload(
                command.getClOrdId(),
                command.getManualDecision(),
                command.getOperatorId(),
                command.getApprovedBy(),
                command.getEvidenceRef(),
                command.getReason(),
                command.getExecutionPrice()
            ),
            command.getCorrelationId()
        );
      } catch (BusinessException ex) {
        if (!isRetriable(ex) || attempt >= attempts) {
          if (isRetriable(ex)) {
            throw new BusinessException(
                ErrorCode.CORE_PROVISIONING_UNAVAILABLE,
                ErrorCode.CORE_PROVISIONING_UNAVAILABLE.defaultMessage(),
                ex
            );
          }
          throw ex;
        }
        lastRetriableFailure = ex;
      }
    }
    throw new BusinessException(
        ErrorCode.CORE_PROVISIONING_UNAVAILABLE,
        ErrorCode.CORE_PROVISIONING_UNAVAILABLE.defaultMessage(),
        lastRetriableFailure
    );
  }

  private FepOrderResult queryStatusAfterReplay(String clOrdId, String correlationId) {
    try {
      return fepClient.queryOrderStatus(clOrdId, correlationId);
    } catch (BusinessException ex) {
      log.warn("Post-replay status query failed for clOrdId={}", clOrdId, ex);
      return null;
    }
  }

  private InternalOrderReplayResult resolveCompleted(
      Order order,
      InternalOrderReplayCommand command,
      FepReplayResult replayResult,
      FepOrderResult statusSnapshot
  ) {
    BigDecimal orderQty = scale(order.getOrderQty());
    BigDecimal executedQty = firstNonNull(
        scale(statusSnapshot == null ? null : statusSnapshot.executedQty()),
        scale(replayResult.executedQty()),
        orderQty
    );
    BigDecimal executedPrice = firstNonNull(
        scale(statusSnapshot == null ? null : statusSnapshot.executedPrice()),
        scale(replayResult.executedPrice()),
        scale(order.getOrderPrice()),
        scale(command.getExecutionPrice())
    );
    BigDecimal leavesQty = firstNonNull(
        scale(statusSnapshot == null ? null : statusSnapshot.leavesQty()),
        subtract(orderQty, executedQty)
    );
    boolean partialFill = orderQty != null && executedQty != null && executedQty.compareTo(orderQty) < 0;
    String status = partialFill ? "PARTIALLY_FILLED" : "FILLED";
    String executionResult = partialFill ? EXECUTION_RESULT_PARTIAL_FILL : EXECUTION_RESULT_FILLED;
    String externalOrderId = resolveExternalOrderId(order, statusSnapshot);
    Instant executedAt = firstNonNull(
        statusSnapshot == null ? null : statusSnapshot.transactTime(),
        replayResult.processedAt()
    );

    order.updateState(status, Order.EXTERNAL_SYNC_CONFIRMED, externalOrderId, null);
    order.updateExecutionSummary(executionResult, executedQty, leavesQty, executedPrice, executedAt);

    return InternalOrderReplayResult.of(
        order.getClOrdId(),
        FINAL_STATUS_COMPLETED,
        executionResult,
        replayResult.executionSource(),
        executedQty,
        leavesQty,
        executedPrice,
        externalOrderId,
        Order.EXTERNAL_SYNC_CONFIRMED,
        executedAt,
        null,
        replayResult.processedBy(),
        replayResult.processedAt()
    );
  }

  private InternalOrderReplayResult resolveRejected(
      Order order,
      FepReplayResult replayResult,
      FepOrderResult statusSnapshot
  ) {
    String externalOrderId = resolveExternalOrderId(order, statusSnapshot);
    order.updateState("REJECTED", Order.EXTERNAL_SYNC_CONFIRMED, externalOrderId, FAILURE_REASON_MANUAL_REJECT);
    order.updateExecutionSummary(null, null, null, null, null);

    return InternalOrderReplayResult.of(
        order.getClOrdId(),
        FINAL_STATUS_FAILED,
        null,
        null,
        null,
        null,
        null,
        externalOrderId,
        Order.EXTERNAL_SYNC_CONFIRMED,
        null,
        null,
        replayResult.processedBy(),
        replayResult.processedAt()
    );
  }

  private InternalOrderReplayResult resolveCanceled(
      Order order,
      FepReplayResult replayResult,
      FepOrderResult statusSnapshot
  ) {
    BigDecimal orderQty = scale(order.getOrderQty());
    BigDecimal executedQty = firstNonNull(
        scale(statusSnapshot == null ? null : statusSnapshot.executedQty()),
        scale(replayResult.executedQty())
    );
    BigDecimal executedPrice = firstNonNull(
        scale(statusSnapshot == null ? null : statusSnapshot.executedPrice()),
        scale(replayResult.executedPrice())
    );
    boolean partialFillCancel = EXECUTION_RESULT_PARTIAL_FILL_CANCEL.equals(replayResult.executionResult())
        || (executedQty != null && executedQty.signum() > 0);
    BigDecimal leavesQty = partialFillCancel
        ? firstNonNull(scale(statusSnapshot == null ? null : statusSnapshot.canceledQty()), subtract(orderQty, executedQty))
        : orderQty;
    String executionResult = partialFillCancel ? EXECUTION_RESULT_PARTIAL_FILL_CANCEL : EXECUTION_RESULT_CANCELED;
    String externalOrderId = resolveExternalOrderId(order, statusSnapshot);
    Instant executedAt = partialFillCancel
        ? firstNonNull(statusSnapshot == null ? null : statusSnapshot.transactTime(), replayResult.processedAt())
        : null;
    Instant canceledAt = replayResult.processedAt();

    order.updateState("CANCELED", Order.EXTERNAL_SYNC_CONFIRMED, externalOrderId, null);
    order.updateExecutionSummary(
        executionResult,
        partialFillCancel ? executedQty : null,
        leavesQty,
        partialFillCancel ? executedPrice : null,
        executedAt
    );

    return InternalOrderReplayResult.of(
        order.getClOrdId(),
        FINAL_STATUS_CANCELED,
        executionResult,
        null,
        partialFillCancel ? executedQty : null,
        leavesQty,
        partialFillCancel ? executedPrice : null,
        externalOrderId,
        Order.EXTERNAL_SYNC_CONFIRMED,
        executedAt,
        canceledAt,
        replayResult.processedBy(),
        replayResult.processedAt()
    );
  }

  private boolean isRetriable(BusinessException ex) {
    return ex.getErrorCode() == ErrorCode.FEP_GATEWAY_TIMEOUT
        || ex.getErrorCode() == ErrorCode.FEP_GATEWAY_UNAVAILABLE;
  }

  private String resolveExternalOrderId(Order order, FepOrderResult statusSnapshot) {
    if (statusSnapshot != null && statusSnapshot.fepOrderId() != null && !statusSnapshot.fepOrderId().isBlank()) {
      return statusSnapshot.fepOrderId();
    }
    return order.getFepReferenceId();
  }

  private BigDecimal subtract(BigDecimal left, BigDecimal right) {
    if (left == null || right == null) {
      return null;
    }
    return scale(left.subtract(right));
  }

  private BigDecimal scale(BigDecimal value) {
    if (value == null) {
      return null;
    }
    return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal scale(Long value) {
    if (value == null) {
      return null;
    }
    return BigDecimal.valueOf(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  @SafeVarargs
  private final <T> T firstNonNull(T... candidates) {
    for (T candidate : candidates) {
      if (candidate != null) {
        return candidate;
      }
    }
    return null;
  }
}
