package com.fix.channel.service;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.entity.AuditAction;
import com.fix.channel.entity.AuditLog;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.entity.OrderSessionStatus;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.vo.AdminActorContext;
import com.fix.channel.vo.AdminOrderIdempotencyReconciliationResult;
import com.fix.channel.vo.CorebankOrderSnapshotResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminOrderIdempotencyReconciliationService {

  private static final String ORDER_SESSION_TARGET_TYPE = "ORDER_SESSION";
  private static final String SOURCE_SYSTEMS = "CHANNEL|COREBANK|FEP";
  private static final String OUTCOME_RESTORED = "RESTORED";
  private static final String OUTCOME_MISMATCH = "MISMATCH";
  private static final String OUTCOME_FAILED = "FAILED";
  private static final String SYNC_STATUS_CONFIRMED = "CONFIRMED";
  private static final String FAILURE_REASON_SESSION_NOT_EXECUTION_ELIGIBLE = "SESSION_NOT_EXECUTION_ELIGIBLE";
  private static final String FAILURE_REASON_DOWNSTREAM_SYNC_UNRESOLVED = "DOWNSTREAM_SYNC_UNRESOLVED";
  private static final String DOWNSTREAM_CL_ORD_ID_MISMATCH = "DOWNSTREAM_CL_ORD_ID_MISMATCH";

  private final OrderSessionRepository orderSessionRepository;
  private final OrderSessionService orderSessionService;
  private final CorebankClient corebankClient;
  private final AuditLogService auditLogService;
  private final TransactionTemplate transactionTemplate;
  private final Counter runSuccessCounter;
  private final Counter runFailedCounter;
  private final Counter restoredRecordCounter;
  private final Counter mismatchedRecordCounter;
  private final Counter failedRecordCounter;

  public AdminOrderIdempotencyReconciliationService(
      OrderSessionRepository orderSessionRepository,
      OrderSessionService orderSessionService,
      CorebankClient corebankClient,
      AuditLogService auditLogService,
      MeterRegistry meterRegistry,
      PlatformTransactionManager transactionManager
  ) {
    this.orderSessionRepository = orderSessionRepository;
    this.orderSessionService = orderSessionService;
    this.corebankClient = corebankClient;
    this.auditLogService = auditLogService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.runSuccessCounter = Counter.builder("channel.order.idempotency.reconciliation.runs")
        .tag("outcome", "success")
        .register(meterRegistry);
    this.runFailedCounter = Counter.builder("channel.order.idempotency.reconciliation.runs")
        .tag("outcome", "failed")
        .register(meterRegistry);
    this.restoredRecordCounter = Counter.builder("channel.order.idempotency.reconciliation.records")
        .tag("result", "restored")
        .register(meterRegistry);
    this.mismatchedRecordCounter = Counter.builder("channel.order.idempotency.reconciliation.records")
        .tag("result", "mismatch")
        .register(meterRegistry);
    this.failedRecordCounter = Counter.builder("channel.order.idempotency.reconciliation.records")
        .tag("result", "failed")
        .register(meterRegistry);
  }

  public AdminOrderIdempotencyReconciliationResult reconcile(String clOrdId, AdminActorContext actor) {
    OrderSession session = orderSessionRepository.findByClOrdId(clOrdId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_SESSION_NOT_FOUND, "Order session not found."));
    if (!isReconciliationEligible(session)) {
      return recordFailure(
          session,
          actor,
          null,
          FAILURE_REASON_SESSION_NOT_EXECUTION_ELIGIBLE,
          "order session is not in a post-execution reconciliation state"
      );
    }

    try {
      CorebankOrderSnapshotResult snapshot = corebankClient.getOrderSnapshot(clOrdId, actor.getCorrelationId());
      String mismatchType = detectMismatchType(session, snapshot);
      if (mismatchType != null) {
        return recordMismatch(session, actor, snapshot, mismatchType, "canonical state mismatch detected");
      }

      CorebankOrderSnapshotResult reconciledSnapshot = refreshIfNeeded(snapshot, session, actor.getCorrelationId());
      mismatchType = detectMismatchType(session, reconciledSnapshot);
      if (mismatchType != null) {
        return recordMismatch(session, actor, reconciledSnapshot, mismatchType, "canonical state mismatch detected");
      }
      if (!hasConfirmedExternalLinkage(reconciledSnapshot)) {
        return recordFailure(
            session,
            actor,
            reconciledSnapshot,
            FAILURE_REASON_DOWNSTREAM_SYNC_UNRESOLVED,
            "downstream reconciliation did not reach confirmed external linkage"
        );
      }
      return reconcileWithShortLock(clOrdId, actor, reconciledSnapshot);
    } catch (BusinessException ex) {
      String mismatchType = mismatchTypeFor(ex);
      if (mismatchType != null) {
        return recordMismatch(session, actor, null, mismatchType, ex.getMessage());
      }
      return recordFailure(session, actor, null, ex.getErrorCode().name(), ex.getMessage());
    }
  }

  private AdminOrderIdempotencyReconciliationResult reconcileWithShortLock(
      String clOrdId,
      AdminActorContext actor,
      CorebankOrderSnapshotResult snapshot
  ) {
    AdminOrderIdempotencyReconciliationResult result = transactionTemplate.execute(status -> {
      OrderSession lockedSession = orderSessionRepository.findByClOrdIdForUpdate(clOrdId)
          .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_SESSION_NOT_FOUND, "Order session not found."));
      if (!isReconciliationEligible(lockedSession)) {
        return recordFailure(
            lockedSession,
            actor,
            snapshot,
            FAILURE_REASON_SESSION_NOT_EXECUTION_ELIGIBLE,
            "order session is not in a post-execution reconciliation state"
        );
      }

      String mismatchType = detectMismatchType(lockedSession, snapshot);
      if (mismatchType != null) {
        return recordMismatch(lockedSession, actor, snapshot, mismatchType, "canonical state mismatch detected");
      }
      if (!hasConfirmedExternalLinkage(snapshot)) {
        return recordFailure(
            lockedSession,
            actor,
            snapshot,
            FAILURE_REASON_DOWNSTREAM_SYNC_UNRESOLVED,
            "downstream reconciliation did not reach confirmed external linkage"
        );
      }

      OrderSession reconciledSession = orderSessionService.reconcileExternalLinkage(
          lockedSession,
          snapshot.getExternalOrderId(),
          snapshot.getExternalSyncStatus()
      );
      String detail = "clOrdId=" + clOrdId
          + ", outcome=" + OUTCOME_RESTORED
          + ", sourceSystems=" + SOURCE_SYSTEMS
          + ", externalOrderId=" + snapshot.getExternalOrderId()
          + ", externalSyncStatus=" + snapshot.getExternalSyncStatus()
          + ", actor=" + actor.getAdminEmail();
      recordAudit(reconciledSession, actor, detail);
      runSuccessCounter.increment();
      restoredRecordCounter.increment();
      return AdminOrderIdempotencyReconciliationResult.of(
          clOrdId,
          reconciledSession.getOrderSessionId(),
          OUTCOME_RESTORED,
          null,
          snapshot.getExternalOrderId(),
          snapshot.getExternalSyncStatus(),
          "reconciled external linkage from corebank evidence",
          1,
          1,
          0,
          0
      );
    });
    if (result == null) {
      throw new IllegalStateException("transactional reconciliation must return a result");
    }
    return result;
  }

  private CorebankOrderSnapshotResult refreshIfNeeded(
      CorebankOrderSnapshotResult snapshot,
      OrderSession session,
      String correlationId
  ) {
    if (!needsDownstreamRefresh(snapshot)) {
      return snapshot;
    }
    corebankClient.requeryOrder(snapshot.getClOrdId(), nextRequeryAttemptCount(session), correlationId);
    return corebankClient.getOrderSnapshot(snapshot.getClOrdId(), correlationId);
  }

  private boolean needsDownstreamRefresh(CorebankOrderSnapshotResult snapshot) {
    return !hasConfirmedExternalLinkage(snapshot);
  }

  private boolean hasConfirmedExternalLinkage(CorebankOrderSnapshotResult snapshot) {
    return !isBlank(snapshot.getExternalOrderId())
        && SYNC_STATUS_CONFIRMED.equalsIgnoreCase(snapshot.getExternalSyncStatus());
  }

  private boolean isReconciliationEligible(OrderSession session) {
    return session.isExternalLinkageReconciliationEligible();
  }

  private String detectMismatchType(OrderSession session, CorebankOrderSnapshotResult snapshot) {
    if (!session.getClOrdId().equals(snapshot.getClOrdId())) {
      return "CANONICAL_CL_ORD_ID_MISMATCH";
    }
    if (session.getAccountId() != null
        && snapshot.getAccountId() != null
        && !session.getAccountId().equals(snapshot.getAccountId())) {
      return "ACCOUNT_MISMATCH";
    }
    String businessStateMismatch = detectBusinessStateMismatch(session, snapshot);
    if (businessStateMismatch != null) {
      return businessStateMismatch;
    }
    if (!isBlank(session.getExternalOrderId())
        && !isBlank(snapshot.getExternalOrderId())
        && !session.getExternalOrderId().equals(snapshot.getExternalOrderId())) {
      return "EXTERNAL_REFERENCE_CONFLICT";
    }
    return null;
  }

  private String detectBusinessStateMismatch(OrderSession session, CorebankOrderSnapshotResult snapshot) {
    OrderSessionStatus sessionStatus = session.getStatus();
    if (sessionStatus == null) {
      return null;
    }
    String corebankStatus = normalize(snapshot.getStatus());
    if (corebankStatus == null) {
      return null;
    }
    return switch (corebankStatus) {
      case "FILLED" -> sessionStatus == OrderSessionStatus.COMPLETED ? null : "TERMINAL_STATE_MISMATCH";
      case "CANCELED" -> sessionStatus == OrderSessionStatus.CANCELED ? null : "TERMINAL_STATE_MISMATCH";
      case "PARTIALLY_FILLED", "REJECTED" -> "TERMINAL_STATE_MISMATCH";
      default -> null;
    };
  }

  private String mismatchTypeFor(BusinessException exception) {
    if (exception.getErrorCode() == ErrorCode.CORE_RESOURCE_NOT_FOUND) {
      return "COREBANK_ORDER_MISSING";
    }
    if (exception.getMetadata() != null
        && DOWNSTREAM_CL_ORD_ID_MISMATCH.equals(exception.getMetadata().operatorCode())) {
      return DOWNSTREAM_CL_ORD_ID_MISMATCH;
    }
    if (exception.getErrorCode() == ErrorCode.CONTRACT_VALIDATION_FAILED
        && exception.getMessage() != null
        && exception.getMessage().contains("clOrdId must match request")) {
      return DOWNSTREAM_CL_ORD_ID_MISMATCH;
    }
    return null;
  }

  private AdminOrderIdempotencyReconciliationResult recordMismatch(
      OrderSession session,
      AdminActorContext actor,
      CorebankOrderSnapshotResult snapshot,
      String mismatchType,
      String message
  ) {
    String detail = "clOrdId=" + session.getClOrdId()
        + ", outcome=" + OUTCOME_MISMATCH
        + ", mismatchType=" + mismatchType
        + ", sourceSystems=" + SOURCE_SYSTEMS
        + ", sessionStatus=" + session.getStatus()
        + ", corebankStatus=" + (snapshot == null ? null : snapshot.getStatus())
        + ", sessionAccountId=" + session.getAccountId()
        + ", corebankAccountId=" + (snapshot == null ? null : snapshot.getAccountId())
        + ", sessionExternalOrderId=" + session.getExternalOrderId()
        + ", corebankExternalOrderId=" + (snapshot == null ? null : snapshot.getExternalOrderId())
        + ", actor=" + actor.getAdminEmail();
    recordAudit(session, actor, detail);
    runSuccessCounter.increment();
    mismatchedRecordCounter.increment();
    return AdminOrderIdempotencyReconciliationResult.of(
        session.getClOrdId(),
        session.getOrderSessionId(),
        OUTCOME_MISMATCH,
        mismatchType,
        snapshot == null ? session.getExternalOrderId() : snapshot.getExternalOrderId(),
        snapshot == null ? session.getExternalSyncStatus() : snapshot.getExternalSyncStatus(),
        message,
        1,
        0,
        1,
        0
    );
  }

  private AdminOrderIdempotencyReconciliationResult recordFailure(
      OrderSession session,
      AdminActorContext actor,
      CorebankOrderSnapshotResult snapshot,
      String reason,
      String message
  ) {
    String detail = "clOrdId=" + session.getClOrdId()
        + ", outcome=" + OUTCOME_FAILED
        + ", reason=" + reason
        + ", sourceSystems=" + SOURCE_SYSTEMS
        + ", actor=" + actor.getAdminEmail()
        + ", message=" + message;
    recordAudit(session, actor, detail);
    runFailedCounter.increment();
    failedRecordCounter.increment();
    return AdminOrderIdempotencyReconciliationResult.of(
        session.getClOrdId(),
        session.getOrderSessionId(),
        OUTCOME_FAILED,
        null,
        snapshot == null ? session.getExternalOrderId() : snapshot.getExternalOrderId(),
        snapshot == null ? session.getExternalSyncStatus() : snapshot.getExternalSyncStatus(),
        message,
        1,
        0,
        0,
        1
    );
  }

  private void recordAudit(OrderSession session, AdminActorContext actor, String detail) {
    auditLogService.record(AuditLog.ofOrderSession(
        actor.getAdminMemberId(),
        session.getId(),
        AuditAction.ORDER_SESSION_RECONCILIATION,
        ORDER_SESSION_TARGET_TYPE,
        session.getOrderSessionId(),
        detail,
        actor.getClientIp(),
        actor.getUserAgent(),
        actor.getCorrelationId()
    ));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private int nextRequeryAttemptCount(OrderSession session) {
    return session.getRecoveryAttemptCount() == null ? 1 : session.getRecoveryAttemptCount() + 1;
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }
}
