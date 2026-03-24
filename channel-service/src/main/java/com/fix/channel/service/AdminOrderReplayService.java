package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.support.ManualReplayIdentitySupport;
import com.fix.channel.vo.AdminActorContext;
import com.fix.channel.vo.AdminOrderReplayCommand;
import com.fix.channel.vo.AdminOrderReplayResult;
import com.fix.channel.vo.OrderReplayResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderReplayService {

  private static final String ORDER_SESSION_TARGET_TYPE = "ORDER_SESSION";
  private static final String NOTIFICATION_CHANNEL_ORDER = "ORDER";
  private static final String FINAL_STATUS_COMPLETED = "COMPLETED";
  private static final String FINAL_STATUS_FAILED = "FAILED";
  private static final String FINAL_STATUS_CANCELED = "CANCELED";
  private static final String ORDER_TYPE_MARKET = "MARKET";
  private static final String EXECUTION_SOURCE_VIRTUAL_FILL = "VIRTUAL_FILL";
  private static final String EXECUTION_RESULT_CANCELED = "CANCELED";
  private static final String EXECUTION_RESULT_PARTIAL_FILL_CANCEL = "PARTIAL_FILL_CANCEL";
  private static final String FAILURE_REASON_MANUAL_REJECT = "MANUAL_REJECT";
  private static final String NOTIFICATION_EVENT_ORDER_FILLED = "ORDER_FILLED";
  private static final String NOTIFICATION_EVENT_ORDER_FAILED = "ORDER_FAILED";
  private static final String NOTIFICATION_EVENT_ORDER_CANCELED = "ORDER_CANCELED";
  private static final String NOTIFICATION_EVENT_ORDER_PARTIAL_FILL_CANCEL = "ORDER_PARTIAL_FILL_CANCEL";

  private final OrderSessionRepository orderSessionRepository;
  private final CorebankClient corebankClient;
  private final AuditLogService auditLogService;
  private final ChannelScaffoldService channelScaffoldService;
  private final ManualRecoveryQueueService manualRecoveryQueueService;

  @Transactional
  public AdminOrderReplayResult replay(String clOrdId, AdminOrderReplayCommand command, AdminActorContext actor) {
    OrderSession session = orderSessionRepository.findByClOrdIdForUpdate(clOrdId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_SESSION_NOT_FOUND, "Order session not found."));

    validateGovernance(command, actor);

    String fingerprint = ManualReplayIdentitySupport.replayFingerprint(
        clOrdId,
        command.getManualDecision(),
        command.getApprovedBy(),
        command.getEvidenceRef(),
        command.getReason(),
        command.getExecutionPrice(),
        actor.getOperatorId()
    );

    if (session.getStatus() != OrderSessionStatus.ESCALATED) {
      return resolveTerminalReplay(session, fingerprint, actor);
    }

    OrderReplayResult replayResult = corebankClient.replayOrder(clOrdId, command, actor.getOperatorId(), actor.getCorrelationId());
    Instant processedAt = replayResult.getProcessedAt() != null ? replayResult.getProcessedAt() : Instant.now();

    switch (replayResult.getFinalStatus()) {
      case FINAL_STATUS_COMPLETED -> session.complete(
          replayResult.getExecutionResult(),
          replayResult.getExecutedQty(),
          replayResult.getLeavesQty(),
          replayResult.getExecutedPrice(),
          replayResult.getExternalOrderId(),
          replayResult.getExternalSyncStatus(),
          replayResult.getExecutedAt()
      );
      case FINAL_STATUS_FAILED -> session.fail(FAILURE_REASON_MANUAL_REJECT);
      case FINAL_STATUS_CANCELED -> session.cancel(
          replayExecutionResult(replayResult),
          replayResult.getExecutedQty(),
          replayResult.getLeavesQty(),
          replayResult.getExecutedPrice(),
          replayResult.getExternalOrderId(),
          replayResult.getExternalSyncStatus(),
          replayResult.getExecutedAt(),
          replayResult.getCanceledAt() != null ? replayResult.getCanceledAt() : processedAt
      );
      default -> throw new BusinessException(ErrorCode.INTERNAL_ERROR, "unsupported replay final status");
    }

    session.recordManualReplayOutcome(
        fingerprint,
        actor.getOperatorId(),
        replayResult.getExecutionSource(),
        processedAt
    );
    orderSessionRepository.flush();
    manualRecoveryQueueService.resolveIfPresent(
        session.getOrderSessionId(),
        actor.getOperatorId(),
        session.getStatus().name(),
        processedAt
    );

    auditLogService.record(AuditLog.ofOrderSession(
        actor.getAdminMemberId(),
        session.getId(),
        AuditAction.MANUAL_REPLAY,
        ORDER_SESSION_TARGET_TYPE,
        session.getOrderSessionId(),
        auditDetail(session, command, replayResult, actor, processedAt),
        actor.getClientIp(),
        actor.getUserAgent(),
        actor.getCorrelationId()
    ));
    publishTerminalNotification(session);

    return toPublicResult(session);
  }

  private void validateGovernance(AdminOrderReplayCommand command, AdminActorContext actor) {
    if (actor.getOperatorId().equalsIgnoreCase(command.getApprovedBy())) {
      throw new BusinessException(
          ErrorCode.MANUAL_REPLAY_GOVERNANCE_FAILED,
          "approvedBy must differ from operatorId"
      );
    }
  }

  private AdminOrderReplayResult resolveTerminalReplay(
      OrderSession session,
      String fingerprint,
      AdminActorContext actor
  ) {
    if (session.getManualReplayFingerprint() == null || session.getManualReplayFingerprint().isBlank()) {
      throw new BusinessException(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED, "Replay target is not escalated");
    }
    if (!session.matchesManualReplayFingerprint(fingerprint)) {
      throw new BusinessException(
          ErrorCode.ORDER_SESSION_NOT_AUTHORIZED,
          "manual replay payload conflicts with the previously processed result"
      );
    }
    manualRecoveryQueueService.resolveIfPresent(
        session.getOrderSessionId(),
        session.getManualReplayProcessedBy() != null ? session.getManualReplayProcessedBy() : actor.getOperatorId(),
        session.getStatus().name(),
        session.getManualReplayProcessedAt() != null ? session.getManualReplayProcessedAt() : Instant.now()
    );
    return toPublicResult(session);
  }

  private String replayExecutionResult(OrderReplayResult replayResult) {
    if (replayResult.getExecutionResult() != null && !replayResult.getExecutionResult().isBlank()) {
      return replayResult.getExecutionResult();
    }
    return EXECUTION_RESULT_CANCELED;
  }

  private String auditDetail(
      OrderSession session,
      AdminOrderReplayCommand command,
      OrderReplayResult replayResult,
      AdminActorContext actor,
      Instant processedAt
  ) {
    return String.join(
        ",",
        detailEntry("clOrdId", session.getClOrdId()),
        detailEntry("sessionMemberId", session.getMemberId()),
        detailEntry("operatorId", actor.getOperatorId()),
        detailEntry("adminEmail", actor.getAdminEmail()),
        detailEntry("approvedBy", command.getApprovedBy()),
        detailEntry("manualDecision", command.getManualDecision()),
        detailEntry("evidenceRef", command.getEvidenceRef()),
        detailEntry("reason", command.getReason()),
        detailEntry("executionPrice", command.getExecutionPrice()),
        detailEntry("executionPriceSource", executionPriceSource(session, command, replayResult)),
        detailEntry("finalStatus", replayResult.getFinalStatus()),
        detailEntry("executionResult", replayResult.getExecutionResult()),
        detailEntry("executionSource", replayResult.getExecutionSource()),
        detailEntry("processedAt", processedAt)
    );
  }

  private String executionPriceSource(
      OrderSession session,
      AdminOrderReplayCommand command,
      OrderReplayResult replayResult
  ) {
    if (command.getExecutionPrice() == null
        || replayResult.getExecutedPrice() == null
        || !ORDER_TYPE_MARKET.equalsIgnoreCase(session.getOrderType())
        || !EXECUTION_SOURCE_VIRTUAL_FILL.equalsIgnoreCase(replayResult.getExecutionSource())) {
      return "";
    }
    BigDecimal operatorPrice = BigDecimal.valueOf(command.getExecutionPrice());
    return replayResult.getExecutedPrice().compareTo(operatorPrice) == 0 ? "MANUAL_INPUT" : "";
  }

  private void publishTerminalNotification(OrderSession session) {
    channelScaffoldService.bootstrapTypedNotification(
        session.getMemberId(),
        NOTIFICATION_CHANNEL_ORDER,
        terminalNotificationMessage(session),
        notificationEvent(session),
        terminalNotificationPayload(session)
    );
  }

  private String terminalNotificationMessage(OrderSession session) {
    StringBuilder message = new StringBuilder()
        .append("orderSessionId=").append(session.getOrderSessionId())
        .append(" event=").append(notificationEvent(session))
        .append(" status=").append(session.getStatus().name())
        .append(" executionResult=").append(nullSafe(session.getExecutionResult()));
    appendField(message, "executionSource", session.getManualReplayExecutionSource());
    appendField(message, "executedQty", session.getExecutedQty());
    appendField(message, "leavesQty", session.getLeavesQty());
    appendField(message, "executedPrice", session.getExecutedPrice());
    appendField(message, "executedAt", session.getExecutedAt());
    if (session.getStatus() == OrderSessionStatus.FAILED) {
      appendField(message, "failureReason", session.getFailureReason());
    }
    if (session.getStatus() == OrderSessionStatus.CANCELED) {
      appendField(message, "canceledAt", session.getCanceledAt());
      if (EXECUTION_RESULT_PARTIAL_FILL_CANCEL.equalsIgnoreCase(session.getExecutionResult())) {
        appendField(message, "canceledQty", session.getLeavesQty());
      }
    }
    return message.toString();
  }

  private String notificationEvent(OrderSession session) {
    if (session.getStatus() == OrderSessionStatus.FAILED) {
      return NOTIFICATION_EVENT_ORDER_FAILED;
    }
    if (session.getStatus() == OrderSessionStatus.CANCELED) {
      return EXECUTION_RESULT_PARTIAL_FILL_CANCEL.equalsIgnoreCase(session.getExecutionResult())
          ? NOTIFICATION_EVENT_ORDER_PARTIAL_FILL_CANCEL
          : NOTIFICATION_EVENT_ORDER_CANCELED;
    }
    return NOTIFICATION_EVENT_ORDER_FILLED;
  }

  private Map<String, Object> terminalNotificationPayload(OrderSession session) {
    String eventType = notificationEvent(session);
    LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", eventType);
    payload.put("orderSessionId", session.getOrderSessionId());
    payload.put("clOrdId", session.getClOrdId());
    payload.put("symbol", session.getSymbol());
    payload.put("side", session.getSide());

    if (NOTIFICATION_EVENT_ORDER_FAILED.equals(eventType)) {
      putIfNotNull(payload, "failureReason", session.getFailureReason());
    } else if (NOTIFICATION_EVENT_ORDER_CANCELED.equals(eventType)) {
      putIfNotNull(payload, "canceledQty", resolveCanceledQty(session));
      putIfNotNull(payload, "canceledAt", session.getCanceledAt());
    } else if (NOTIFICATION_EVENT_ORDER_PARTIAL_FILL_CANCEL.equals(eventType)) {
      putIfNotNull(payload, "executedQty", session.getExecutedQty());
      putIfNotNull(payload, "canceledQty", resolveCanceledQty(session));
      putIfNotNull(payload, "executedPrice", session.getExecutedPrice());
      putIfNotNull(payload, "executedAt", session.getExecutedAt());
    } else {
      putIfNotNull(payload, "executionResult", session.getExecutionResult());
      putIfNotNull(payload, "executedQty", session.getExecutedQty());
      putIfNotNull(payload, "leavesQty", session.getLeavesQty());
      putIfNotNull(payload, "executedPrice", session.getExecutedPrice());
      putIfNotNull(payload, "executedAt", session.getExecutedAt());
    }

    putIfNotNull(payload, "timestamp", session.getManualReplayProcessedAt());
    return payload;
  }

  private AdminOrderReplayResult toPublicResult(OrderSession session) {
    return AdminOrderReplayResult.of(
        session.getClOrdId(),
        session.getStatus().name(),
        session.getExecutionResult(),
        session.getManualReplayExecutionSource(),
        session.getExecutedQty(),
        session.getExecutedPrice(),
        session.getManualReplayProcessedBy(),
        session.getManualReplayProcessedAt()
    );
  }

  private String nullSafe(String value) {
    return value == null ? "" : value;
  }

  private void appendField(StringBuilder builder, String key, Object value) {
    builder.append(" ").append(key).append("=");
    builder.append(value == null ? "" : value);
  }

  private String detailEntry(String key, Object value) {
    return key + "=" + escapeAuditValue(value);
  }

  private String escapeAuditValue(Object value) {
    if (value == null) {
      return "";
    }
    return String.valueOf(value)
        .replace("\\", "\\\\")
        .replace(",", "\\,")
        .replace("=", "\\=")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }

  private BigDecimal resolveCanceledQty(OrderSession session) {
    if (session.getLeavesQty() != null) {
      return session.getLeavesQty();
    }
    if (session.getQty() != null && session.getExecutedQty() != null) {
      return session.getQty().subtract(session.getExecutedQty());
    }
    return session.getQty();
  }

  private void putIfNotNull(Map<String, Object> payload, String key, Object value) {
    if (value != null) {
      payload.put(key, value);
    }
  }
}
