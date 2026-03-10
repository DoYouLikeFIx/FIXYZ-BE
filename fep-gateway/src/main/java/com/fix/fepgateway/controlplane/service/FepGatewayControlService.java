package com.fix.fepgateway.controlplane.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepCancelStatus;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.common.fep.FepReplayFinalStatus;
import com.fix.fepgateway.dataplane.fix.FixDataPlaneService;
import com.fix.fepgateway.entity.GatewayOrder;
import com.fix.fepgateway.entity.GatewayOrderCancel;
import com.fix.fepgateway.entity.GatewayOrderReplay;
import com.fix.fepgateway.repository.GatewayOrderCancelRepository;
import com.fix.fepgateway.repository.GatewayOrderReplayRepository;
import com.fix.fepgateway.repository.GatewayOrderRepository;
import com.fix.fepgateway.service.GatewaySecurityEventService;
import com.fix.fepgateway.vo.GatewayCancelResult;
import com.fix.fepgateway.vo.GatewayExecutionOutcome;
import com.fix.fepgateway.vo.GatewayInternalOrderStatusCommand;
import com.fix.fepgateway.vo.GatewayOrderCancelCommand;
import com.fix.fepgateway.vo.GatewayOrderReplayCommand;
import com.fix.fepgateway.vo.GatewayOrderResult;
import com.fix.fepgateway.vo.GatewayOrderStatusCommand;
import com.fix.fepgateway.vo.GatewayOrderSubmitCommand;
import com.fix.fepgateway.vo.GatewayReplayExecution;
import com.fix.fepgateway.vo.GatewayReplayResult;
import com.fix.fepgateway.vo.FepReplayDecision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FepGatewayControlService {

  private final GatewayOrderRepository gatewayOrderRepository;
  private final GatewayOrderCancelRepository gatewayOrderCancelRepository;
  private final GatewayOrderReplayRepository gatewayOrderReplayRepository;
  private final GatewaySecurityEventService gatewaySecurityEventService;
  private final FixDataPlaneService fixDataPlaneService;

  @Value("${fep.replay.max-virtual-fill-deviation-bps:500}")
  private long maxVirtualFillDeviationBps;

  @Value("${fep.idempotency.reference-retention:10m}")
  private Duration referenceIdRetention;

  @Transactional
  public GatewayOrderResult submitOrder(GatewayOrderSubmitCommand command) {
    Instant now = Instant.now();
    return findExistingSubmit(command)
        .map(existingSubmit -> resolveExistingSubmit(command, existingSubmit, now))
        .orElseGet(() -> createNewSubmit(command, now));
  }

  @Transactional(readOnly = true)
  public GatewayOrderResult status(GatewayOrderStatusCommand command) {
    GatewayOrder order = gatewayOrderRepository.findByClOrdId(command.getClOrdId()).orElse(null);
    return fixDataPlaneService.sendOrderStatusRequest(command.getClOrdId(), order);
  }

  @Transactional
  public GatewayCancelResult cancel(GatewayOrderCancelCommand command) {
    GatewayOrder order = gatewayOrderRepository.findByClOrdId(command.getClOrdId())
        .orElseThrow(() -> new BusinessException(ErrorCode.FEP_ORDER_NOT_FOUND, "order not found in gateway"));

    validateCancelRequest(command, order);

    String cancelClOrdId = UUID.randomUUID().toString();
    GatewayOrderCancel cancelRecord = gatewayOrderCancelRepository.save(
        GatewayOrderCancel.requested(command.getClOrdId(), cancelClOrdId, command.getReason().name())
    );

    Long priorExecutedQty = order.getExecutedQty();
    Long priorExecutedPrice = order.getExecutedPrice();
    Instant priorExecutedAt = order.getTransactTime();

    GatewayExecutionOutcome outcome = fixDataPlaneService.sendCancel(command, order);
    order.applyExecution(outcome);
    order.updateCancelFailureMode("NONE");

    GatewayCancelResult result = priorExecutedQty != null && priorExecutedQty > 0
        ? new GatewayCancelResult(
            command.getClOrdId(),
            cancelClOrdId,
            FepCancelStatus.PARTIAL_FILL_CANCEL,
            priorExecutedQty,
            command.getCancelQty(),
            priorExecutedPrice,
            priorExecutedAt,
            outcome.transactTime()
        )
        : new GatewayCancelResult(
            command.getClOrdId(),
            cancelClOrdId,
            FepCancelStatus.CANCELED,
            null,
            command.getCancelQty(),
            null,
            null,
            outcome.transactTime()
        );

    cancelRecord.complete(
        result.status(),
        result.canceledQty(),
        result.executedQty(),
        result.executedPrice(),
        result.executedAt(),
        result.canceledAt()
    );
    return result;
  }

  @Transactional
  public GatewayReplayResult replay(GatewayOrderReplayCommand command) {
    GatewayOrder order = gatewayOrderRepository.findByClOrdId(command.getClOrdId())
        .orElseThrow(() -> new BusinessException(ErrorCode.FEP_ORDER_NOT_FOUND, "order not found in gateway"));

    if (!order.isReplayEscalated()) {
      throw new BusinessException(ErrorCode.INVALID_SESSION_STATUS, "replay target must be ESCALATED");
    }

    validateReplayRequest(command, order);

    GatewayOrderReplay replayRecord = gatewayOrderReplayRepository.save(
        GatewayOrderReplay.requested(
            command.getClOrdId(),
            command.getManualDecision().name(),
            command.getOperatorId(),
            command.getApprovedBy(),
            command.getEvidenceRef(),
            command.getReason(),
            command.getExecutionPrice()
        )
    );

    GatewayReplayExecution replayExecution = fixDataPlaneService.sendReplay(command, order);
    order.applyExecution(replayExecution.outcome());
    order.updateRecoveryStatus(replayExecution.finalStatus().name());
    order.clearRequeryOutcome();

    GatewayReplayResult result = buildReplayResult(command, replayExecution);
    replayRecord.complete(
        result.finalStatus().name(),
        result.executionSource() != null ? result.executionSource().name() : null,
        result.executionResult() != null ? result.executionResult().name() : null,
        result.processedAt()
    );
    return result;
  }

  @Transactional
  public GatewayOrderResult internalUpdateStatus(GatewayInternalOrderStatusCommand command) {
    GatewayOrder order = gatewayOrderRepository.findByClOrdId(command.getClOrdId())
        .orElseThrow(() -> new BusinessException(ErrorCode.FEP_ORDER_NOT_FOUND, "order not found in gateway"));

    order.applyExecution(resolveInternalStatus(command, order));
    applyInternalControls(command, order);
    return order.toResult(Instant.now());
  }

  private void validateCancelRequest(GatewayOrderCancelCommand command, GatewayOrder order) {
    if (!order.getSymbol().equals(command.getSymbol())) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "symbol must match the original order");
    }
    if (!order.getSide().equals(command.getSide().name())) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "side must match the original order");
    }
    if (order.remainingQty() == 0 || isFinalRejected(order)) {
      throw new BusinessException(ErrorCode.CANCEL_REJECTED, "order is not cancelable");
    }
    if (command.getCancelQty() > order.remainingQty()) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "cancelQty exceeds the remaining order quantity");
    }
  }

  private void validateReplayRequest(GatewayOrderReplayCommand command, GatewayOrder order) {
    if (command.getManualDecision() != FepReplayDecision.APPROVE) {
      return;
    }

    if (order.requiresManualExecutionPrice() && command.getExecutionPrice() == null) {
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "executionPrice is required for MARKET replay approval when order status is unresolved"
      );
    }

    if (order.requiresManualExecutionPrice() && command.getExecutionPrice() != null) {
      order.seedRequestedPriceIfMissing(command.getExecutionPrice());
      validateVirtualFillDeviation(order, command.getExecutionPrice());
    }
  }

  private GatewayReplayResult buildReplayResult(GatewayOrderReplayCommand command, GatewayReplayExecution replayExecution) {
    GatewayExecutionOutcome outcome = replayExecution.outcome();
    boolean hideExecutionPayload = replayExecution.finalStatus() == FepReplayFinalStatus.FAILED
        || (replayExecution.finalStatus() == FepReplayFinalStatus.CANCELED && replayExecution.executionResult() == null);
    return new GatewayReplayResult(
        command.getClOrdId(),
        replayExecution.finalStatus(),
        replayExecution.executionResult(),
        replayExecution.executionSource(),
        hideExecutionPayload ? null : outcome.executedQty(),
        hideExecutionPayload ? null : outcome.executedPrice(),
        command.getOperatorId(),
        outcome.transactTime()
    );
  }

  private GatewayExecutionOutcome resolveInternalStatus(GatewayInternalOrderStatusCommand command, GatewayOrder order) {
    FepOrdStatus status = parseStatus(command.getStatus());
    long totalQty = order.totalQty();
    Long requestedPrice = order.getRequestedPrice();
    long existingExecutedQty = order.getExecutedQty() == null ? 0L : order.getExecutedQty();
    Long existingExecutedPrice = order.getExecutedPrice() != null ? order.getExecutedPrice() : requestedPrice;
    Instant existingTransactTime = order.getTransactTime();
    Long requestedExecutedQty = command.getExecutedQty();
    Long requestedExecutedPrice = command.getExecutedPrice();
    String requestedMessage = normalizeBlank(command.getMessage());
    String requestedRejectReason = normalizeBlank(command.getRejectReason());
    String requestedParseError = normalizeBlank(command.getParseError());
    Instant now = Instant.now();

    return switch (status) {
      case FILLED -> {
        if (requestedExecutedQty != null && requestedExecutedQty != totalQty) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "executedQty for FILLED must match the total order quantity"
          );
        }
        Long filledPrice = requestedExecutedPrice != null ? requestedExecutedPrice : existingExecutedPrice;
        if (filledPrice == null) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "executedPrice is required for FILLED updates when no prior execution price exists"
          );
        }
        yield new GatewayExecutionOutcome(
            order.getFepOrderId(),
            FepExecType.FILL,
            FepOrdStatus.FILLED,
            totalQty,
            filledPrice,
            0L,
            now,
            null,
            null,
            null
        );
      }
      case PARTIALLY_FILLED -> {
        long partialExecutedQty = requestedExecutedQty != null
            ? requestedExecutedQty
            : existingExecutedQty;
        if (partialExecutedQty <= 0 || partialExecutedQty >= totalQty) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "executedQty for PARTIALLY_FILLED must be between 1 and totalQty - 1"
          );
        }
        Long partialExecutedPrice = requestedExecutedPrice != null ? requestedExecutedPrice : existingExecutedPrice;
        if (partialExecutedPrice == null) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "executedPrice is required for PARTIALLY_FILLED updates when no prior execution price exists"
          );
        }
        yield new GatewayExecutionOutcome(
            order.getFepOrderId(),
            FepExecType.PARTIAL_FILL,
            FepOrdStatus.PARTIALLY_FILLED,
            partialExecutedQty,
            partialExecutedPrice,
            totalQty - partialExecutedQty,
            now,
            null,
            null,
            null
        );
      }
      case PENDING, UNKNOWN -> {
        long preservedExecutedQty = requestedExecutedQty != null ? requestedExecutedQty : existingExecutedQty;
        if (preservedExecutedQty < 0 || preservedExecutedQty > totalQty) {
          throw new BusinessException(
            ErrorCode.CONTRACT_VALIDATION_FAILED,
            "executedQty must be between 0 and totalQty"
          );
        }
        Long preservedExecutedPrice = requestedExecutedPrice != null ? requestedExecutedPrice : existingExecutedPrice;
        if (preservedExecutedQty > 0 && preservedExecutedPrice == null) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "executedPrice is required when executedQty is provided"
          );
        }
        Instant preservedTransactTime = preservedExecutedQty > 0
            ? (existingTransactTime != null ? existingTransactTime : now)
            : null;
        yield new GatewayExecutionOutcome(
            preservedExecutedQty > 0 ? order.getFepOrderId() : null,
            preservedExecutedQty > 0 ? FepExecType.PARTIAL_FILL : null,
            status,
            preservedExecutedQty > 0 ? preservedExecutedQty : null,
            preservedExecutedQty > 0 ? preservedExecutedPrice : null,
            preservedExecutedQty > 0 ? totalQty - preservedExecutedQty : null,
            preservedTransactTime,
            firstNonBlank(requestedMessage, defaultStatusMessage(status)),
            null,
            null
        );
      }
      case MALFORMED -> {
        long preservedExecutedQty = requestedExecutedQty != null ? requestedExecutedQty : existingExecutedQty;
        if (preservedExecutedQty < 0 || preservedExecutedQty > totalQty) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "executedQty must be between 0 and totalQty"
          );
        }
        Long preservedExecutedPrice = requestedExecutedPrice != null ? requestedExecutedPrice : existingExecutedPrice;
        if (preservedExecutedQty > 0 && preservedExecutedPrice == null) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "executedPrice is required when executedQty is provided"
          );
        }
        Instant preservedTransactTime = preservedExecutedQty > 0
            ? (existingTransactTime != null ? existingTransactTime : now)
            : null;
        yield new GatewayExecutionOutcome(
            preservedExecutedQty > 0 ? order.getFepOrderId() : null,
            preservedExecutedQty > 0 ? FepExecType.PARTIAL_FILL : null,
            status,
            preservedExecutedQty > 0 ? preservedExecutedQty : null,
            preservedExecutedQty > 0 ? preservedExecutedPrice : null,
            preservedExecutedQty > 0 ? totalQty - preservedExecutedQty : null,
            preservedTransactTime,
            firstNonBlank(
                requestedMessage,
                "FIX ExecutionReport parse failed; manual review required"
            ),
            null,
            firstNonBlank(requestedParseError, "PARSE_ERROR:UNKNOWN")
        );
      }
      case REJECTED -> new GatewayExecutionOutcome(
          null,
          FepExecType.REJECTED,
          FepOrdStatus.REJECTED,
          null,
          null,
          null,
          now,
          null,
          firstNonBlank(requestedRejectReason, "OTHER"),
          null
      );
      case CANCELED -> {
        long canceledExecutedQty = requestedExecutedQty != null ? requestedExecutedQty : existingExecutedQty;
        if (canceledExecutedQty < 0 || canceledExecutedQty > totalQty) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "executedQty must be between 0 and totalQty"
          );
        }
        Long canceledExecutedPrice = requestedExecutedPrice != null ? requestedExecutedPrice : existingExecutedPrice;
        if (canceledExecutedQty > 0 && canceledExecutedPrice == null) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "executedPrice is required when canceled orders retain executed quantity"
          );
        }
        yield new GatewayExecutionOutcome(
            order.getFepOrderId(),
            FepExecType.CANCELED,
            FepOrdStatus.CANCELED,
            canceledExecutedQty,
            canceledExecutedQty > 0 ? canceledExecutedPrice : null,
            0L,
            now,
            null,
            null,
            null
        );
      }
    };
  }

  private void applyInternalControls(GatewayInternalOrderStatusCommand command, GatewayOrder order) {
    if (command.getReferencePrice() != null) {
      order.updateRequestedPrice(command.getReferencePrice());
    }

    String recoveryStatus = parseRecoveryStatus(command.getRecoveryStatus());
    if (recoveryStatus != null) {
      order.updateRecoveryStatus(recoveryStatus);
    }

    String cancelFailureMode = parseCancelFailureMode(command.getCancelFailureMode());
    if (cancelFailureMode != null) {
      order.updateCancelFailureMode(cancelFailureMode);
    }

    if (command.getRequeryStatus() != null && !command.getRequeryStatus().isBlank()) {
      FepOrdStatus requeryStatus = parseStatus(command.getRequeryStatus());
      validateRequeryOutcome(order, requeryStatus, command.getRequeryExecutedQty(), command.getRequeryExecutedPrice());
      order.configureRequeryOutcome(
          requeryStatus.name(),
          command.getRequeryExecutedQty(),
          command.getRequeryExecutedPrice()
      );
    }
  }

  private void validateVirtualFillDeviation(GatewayOrder order, Long executionPrice) {
    Long referencePrice = order.referencePrice();
    if (referencePrice == null || referencePrice <= 0) {
      throw new BusinessException(
          ErrorCode.VIRTUAL_FILL_DEVIATION_EXCEEDED,
          "reference price is unavailable for virtual fill deviation validation"
      );
    }

    BigDecimal absoluteDifference = BigDecimal.valueOf(executionPrice).subtract(BigDecimal.valueOf(referencePrice)).abs();
    BigDecimal deviationBps = absoluteDifference
        .multiply(BigDecimal.valueOf(10_000))
        .divide(BigDecimal.valueOf(referencePrice), 4, RoundingMode.HALF_UP);

    if (deviationBps.compareTo(BigDecimal.valueOf(maxVirtualFillDeviationBps)) > 0) {
      throw new BusinessException(
          ErrorCode.VIRTUAL_FILL_DEVIATION_EXCEEDED,
          "executionPrice exceeds the maxVirtualFillDeviationBps threshold"
      );
    }
  }

  private FepOrdStatus parseStatus(String requestedStatus) {
    try {
      return FepOrdStatus.valueOf(requestedStatus.trim().toUpperCase(Locale.ROOT));
    } catch (Exception exception) {
      throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "unsupported order status");
    }
  }

  private String parseRecoveryStatus(String requestedRecoveryStatus) {
    if (requestedRecoveryStatus == null || requestedRecoveryStatus.isBlank()) {
      return null;
    }
    String normalized = requestedRecoveryStatus.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "ACTIVE", "ESCALATED", "COMPLETED", "FAILED", "CANCELED" -> normalized;
      default -> throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "unsupported recovery status");
    };
  }

  private String parseCancelFailureMode(String requestedFailureMode) {
    if (requestedFailureMode == null || requestedFailureMode.isBlank()) {
      return null;
    }
    String normalized = requestedFailureMode.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "NONE", "TIMEOUT", "REJECT" -> normalized;
      default -> throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "unsupported cancel failure mode");
    };
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private String normalizeBlank(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String defaultStatusMessage(FepOrdStatus status) {
    return switch (status) {
      case UNKNOWN -> "execution state is unresolved in external system";
      case PENDING -> "execution report is still pending";
      default -> null;
    };
  }

  private void validateRequeryOutcome(
      GatewayOrder order,
      FepOrdStatus requeryStatus,
      Long requeryExecutedQty,
      Long requeryExecutedPrice
  ) {
    long totalQty = order.totalQty();
    switch (requeryStatus) {
      case FILLED -> {
        if (requeryExecutedQty != null && requeryExecutedQty != totalQty) {
          throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "requery executedQty for FILLED must match totalQty");
        }
        if (resolveExecutionPrice(order, requeryExecutedPrice) == null) {
          throw new BusinessException(ErrorCode.CONTRACT_VALIDATION_FAILED, "requery executedPrice is required for FILLED");
        }
      }
      case PARTIALLY_FILLED -> {
        if (requeryExecutedQty == null || requeryExecutedQty <= 0 || requeryExecutedQty >= totalQty) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "requery executedQty for PARTIALLY_FILLED must be between 1 and totalQty - 1"
          );
        }
        if (resolveExecutionPrice(order, requeryExecutedPrice) == null) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "requery executedPrice is required for PARTIALLY_FILLED"
          );
        }
      }
      case CANCELED -> {
        if (requeryExecutedQty != null && (requeryExecutedQty < 0 || requeryExecutedQty > totalQty)) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "requery executedQty for CANCELED must be between 0 and totalQty"
          );
        }
        if (requeryExecutedQty != null && requeryExecutedQty > 0 && resolveExecutionPrice(order, requeryExecutedPrice) == null) {
          throw new BusinessException(
              ErrorCode.CONTRACT_VALIDATION_FAILED,
              "requery executedPrice is required when CANCELED retains executed quantity"
          );
        }
      }
      case UNKNOWN, PENDING, MALFORMED -> {
        // Replay-time executionPrice validation is enforced on the replay request itself.
      }
      case REJECTED -> {
        // No additional payload required.
      }
    }
  }

  private Long resolveExecutionPrice(GatewayOrder order, Long requestedPrice) {
    if (requestedPrice != null) {
      return requestedPrice;
    }
    return order.referencePrice();
  }

  private boolean isFinalRejected(GatewayOrder order) {
    return FepOrdStatus.REJECTED.name().equals(order.getStatus());
  }

  private Optional<ExistingSubmit> findExistingSubmit(GatewayOrderSubmitCommand command) {
    return gatewayOrderRepository.findByReferenceId(command.referenceId())
        .map(order -> new ExistingSubmit(order, SubmitMatchType.REFERENCE_ID))
        .or(() -> gatewayOrderRepository.findByClOrdId(command.clOrdId())
            .map(order -> new ExistingSubmit(order, SubmitMatchType.CL_ORD_ID)));
  }

  private GatewayOrderResult resolveExistingSubmit(
      GatewayOrderSubmitCommand command,
      ExistingSubmit existingSubmit,
      Instant now
  ) {
    validateExistingSubmit(command, existingSubmit, now);
    return existingSubmit.order().toResult(null);
  }

  private GatewayOrderResult createNewSubmit(GatewayOrderSubmitCommand command, Instant now) {
    GatewayOrder order = GatewayOrder.received(
        command.clOrdId(),
        command.accountId(),
        command.referenceId(),
        now.plus(referenceIdRetention),
        command.symbol(),
        command.side().name(),
        BigDecimal.valueOf(command.qty()),
        command.orderType().name(),
        command.orderType().name().equals("MARKET") ? command.preTradePrice() : command.price(),
        "FIX"
    );

    try {
      GatewayOrder persisted = gatewayOrderRepository.saveAndFlush(order);
      persisted.applyExecution(fixDataPlaneService.sendNewOrder(command));
      return persisted.toResult(null);
    } catch (DataIntegrityViolationException ex) {
      ExistingSubmit existingSubmit = findExistingSubmit(command).orElseThrow(() -> ex);
      validateExistingSubmit(command, existingSubmit, now);
      return existingSubmit.order().toResult(null);
    }
  }

  private void validateExistingSubmit(
      GatewayOrderSubmitCommand command,
      ExistingSubmit existingSubmit,
      Instant now
  ) {
    GatewayOrder order = existingSubmit.order();
    if (!order.isOwnedBy(command.accountId())) {
      recordDeniedReplay(
          "REFERENCE_ID_OWNER_MISMATCH",
          order,
          command.referenceId(),
          command,
          "referenceId cannot be reused by a different account"
      );
      throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "referenceId belongs to a different account");
    }

    if (!order.usesReferenceId(command.referenceId())) {
      recordDeniedReplay(
          "CL_ORD_ID_REFERENCE_ID_MISMATCH",
          order,
          command.referenceId(),
          command,
          "clOrdId is already bound to a different referenceId"
      );
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "clOrdId cannot be reused with a different referenceId"
      );
    }

    if (order.isReferenceIdExpired(now)) {
      recordDeniedReplay(
          "REFERENCE_ID_EXPIRED",
          order,
          command.referenceId(),
          command,
          "referenceId has expired and cannot be reused"
      );
      throw new BusinessException(
          ErrorCode.CONTRACT_VALIDATION_FAILED,
          "referenceId has expired; submit a new external request identity"
      );
    }
  }

  private void recordDeniedReplay(
      String eventType,
      GatewayOrder order,
      String referenceId,
      GatewayOrderSubmitCommand command,
      String detail
  ) {
    gatewaySecurityEventService.recordDeniedReplay(
        eventType,
        referenceId,
        order,
        command.accountId(),
        command.clOrdId(),
        detail
    );
  }

  private record ExistingSubmit(GatewayOrder order, SubmitMatchType matchType) {
  }

  private enum SubmitMatchType {
    REFERENCE_ID,
    CL_ORD_ID
  }
}
