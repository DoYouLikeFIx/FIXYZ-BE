package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.repository.OrderSessionRepository;
import com.fix.channel.vo.AdminActorContext;
import com.fix.channel.vo.CorebankOrderSnapshotResult;
import com.fix.channel.vo.OrderRequeryResult;
import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.ErrorMetadata;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class AdminOrderIdempotencyReconciliationServiceTest {

  private static final String CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174490";

  @Mock
  private OrderSessionRepository orderSessionRepository;
  @Mock
  private OrderSessionService orderSessionService;
  @Mock
  private CorebankClient corebankClient;
  @Mock
  private AuditLogService auditLogService;
  @Mock
  private PlatformTransactionManager transactionManager;

  private SimpleMeterRegistry meterRegistry;
  private AdminOrderIdempotencyReconciliationService reconciliationService;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    reconciliationService = new AdminOrderIdempotencyReconciliationService(
        orderSessionRepository,
        orderSessionService,
        corebankClient,
        auditLogService,
        meterRegistry,
        transactionManager
    );
    lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(new SimpleTransactionStatus());
    lenient().doNothing().when(transactionManager).commit(any());
    lenient().doNothing().when(transactionManager).rollback(any());
  }

  @Test
  void shouldRestoreOrderSessionExternalLinkageFromCorebankEvidence() {
    OrderSession session = completedSession(CL_ORD_ID, null, "FAILED");
    OrderSession reconciledSession = completedSession(CL_ORD_ID, "FEP-KRX-" + CL_ORD_ID, "CONFIRMED");
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(corebankClient.getOrderSnapshot(eq(CL_ORD_ID), eq(actor.getCorrelationId())))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "FILLED",
            "FAILED",
            null
        ))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "FILLED",
            "CONFIRMED",
            "FEP-KRX-" + CL_ORD_ID
        ));
    when(corebankClient.requeryOrder(eq(CL_ORD_ID), eq(1), eq(actor.getCorrelationId())))
        .thenReturn(OrderRequeryResult.of(
            9001L,
            CL_ORD_ID,
            "FILLED",
            "CONFIRMED",
            "FILLED",
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.valueOf(72000),
            "FEP-KRX-" + CL_ORD_ID,
            Instant.parse("2026-03-23T02:00:00Z"),
            null,
            null,
            false,
            false,
            1,
            5
        ));
    when(orderSessionService.reconcileExternalLinkage(
        session,
        "FEP-KRX-" + CL_ORD_ID,
        "CONFIRMED"
    )).thenReturn(reconciledSession);

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    InOrder inOrder = inOrder(orderSessionRepository, corebankClient, orderSessionService);
    inOrder.verify(orderSessionRepository).findByClOrdId(CL_ORD_ID);
    inOrder.verify(corebankClient).getOrderSnapshot(CL_ORD_ID, actor.getCorrelationId());
    inOrder.verify(corebankClient).requeryOrder(CL_ORD_ID, 1, actor.getCorrelationId());
    inOrder.verify(corebankClient).getOrderSnapshot(CL_ORD_ID, actor.getCorrelationId());
    inOrder.verify(orderSessionRepository).findByClOrdIdForUpdate(CL_ORD_ID);
    inOrder.verify(orderSessionService).reconcileExternalLinkage(session, "FEP-KRX-" + CL_ORD_ID, "CONFIRMED");
    verify(orderSessionService).reconcileExternalLinkage(session, "FEP-KRX-" + CL_ORD_ID, "CONFIRMED");
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECONCILIATION".equals(log.getAction())
            && log.getDetail().contains("clOrdId=" + CL_ORD_ID)
            && log.getDetail().contains("outcome=RESTORED")
            && log.getDetail().contains("externalSyncStatus=CONFIRMED")
    ));
    assertThat(result.getOutcome()).isEqualTo("RESTORED");
    assertThat(result.getOrderSessionId()).isEqualTo(reconciledSession.getOrderSessionId());
    assertThat(result.getExternalOrderId()).isEqualTo("FEP-KRX-" + CL_ORD_ID);
    assertThat(result.getExternalSyncStatus()).isEqualTo("CONFIRMED");
    assertThat(result.getScanned()).isEqualTo(1);
    assertThat(result.getRestored()).isEqualTo(1);
    assertThat(result.getMismatched()).isZero();
    assertThat(result.getFailed()).isZero();
    assertThat(meterRegistry.get("channel.order.idempotency.reconciliation.runs")
        .tag("outcome", "success")
        .counter()
        .count()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("channel.order.idempotency.reconciliation.records")
        .tag("result", "restored")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  @Test
  void shouldUseExistingRecoveryAttemptDepthWhenRefreshingEscalatedSnapshot() {
    OrderSession session = completedSession(CL_ORD_ID, null, "FAILED");
    reserveRecoveryAttempts(session, 3);
    OrderSession reconciledSession = completedSession(CL_ORD_ID, "FEP-KRX-" + CL_ORD_ID, "CONFIRMED");
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(corebankClient.getOrderSnapshot(eq(CL_ORD_ID), eq(actor.getCorrelationId())))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "FILLED",
            "ESCALATED",
            "FEP-KRX-" + CL_ORD_ID
        ))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "FILLED",
            "CONFIRMED",
            "FEP-KRX-" + CL_ORD_ID
        ));
    when(corebankClient.requeryOrder(eq(CL_ORD_ID), eq(4), eq(actor.getCorrelationId())))
        .thenReturn(OrderRequeryResult.of(
            9001L,
            CL_ORD_ID,
            "FILLED",
            "CONFIRMED",
            "FILLED",
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.valueOf(72000),
            "FEP-KRX-" + CL_ORD_ID,
            Instant.parse("2026-03-23T02:00:00Z"),
            null,
            null,
            false,
            false,
            4,
            5
        ));
    when(orderSessionService.reconcileExternalLinkage(
        session,
        "FEP-KRX-" + CL_ORD_ID,
        "CONFIRMED"
    )).thenReturn(reconciledSession);

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    verify(corebankClient).requeryOrder(CL_ORD_ID, 4, actor.getCorrelationId());
    assertThat(result.getOutcome()).isEqualTo("RESTORED");
  }

  @Test
  void shouldTreatPartiallyFilledSnapshotAsCompletedForReconciliation() {
    OrderSession session = completedSession(CL_ORD_ID, null, "FAILED");
    OrderSession reconciledSession = completedSession(CL_ORD_ID, "FEP-KRX-" + CL_ORD_ID, "CONFIRMED");
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(corebankClient.getOrderSnapshot(eq(CL_ORD_ID), eq(actor.getCorrelationId())))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "PARTIALLY_FILLED",
            "CONFIRMED",
            "FEP-KRX-" + CL_ORD_ID
        ));
    when(orderSessionService.reconcileExternalLinkage(
        session,
        "FEP-KRX-" + CL_ORD_ID,
        "CONFIRMED"
    )).thenReturn(reconciledSession);

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    verify(orderSessionService).reconcileExternalLinkage(session, "FEP-KRX-" + CL_ORD_ID, "CONFIRMED");
    assertThat(result.getOutcome()).isEqualTo("RESTORED");
  }

  @Test
  void shouldTreatAcceptedSnapshotAsCompletedForReconciliation() {
    OrderSession session = completedSession(CL_ORD_ID, null, "FAILED");
    OrderSession reconciledSession = completedSession(CL_ORD_ID, "FEP-KRX-" + CL_ORD_ID, "CONFIRMED");
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(corebankClient.getOrderSnapshot(eq(CL_ORD_ID), eq(actor.getCorrelationId())))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "ACCEPTED",
            "CONFIRMED",
            "FEP-KRX-" + CL_ORD_ID
        ));
    when(orderSessionService.reconcileExternalLinkage(
        session,
        "FEP-KRX-" + CL_ORD_ID,
        "CONFIRMED"
    )).thenReturn(reconciledSession);

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    verify(orderSessionService).reconcileExternalLinkage(session, "FEP-KRX-" + CL_ORD_ID, "CONFIRMED");
    assertThat(result.getOutcome()).isEqualTo("RESTORED");
  }

  @Test
  void shouldSurfaceMismatchWithoutMutatingCanonicalOrderSessionState() {
    OrderSession session = completedSession(CL_ORD_ID, "FEP-LOCAL-1", "CONFIRMED");
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(corebankClient.getOrderSnapshot(eq(CL_ORD_ID), eq(actor.getCorrelationId())))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            999L,
            CL_ORD_ID,
            "FILLED",
            "CONFIRMED",
            "FEP-LOCAL-1"
        ));

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    verify(orderSessionService, never()).reconcileExternalLinkage(any(), any(), any());
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECONCILIATION".equals(log.getAction())
            && log.getDetail().contains("outcome=MISMATCH")
            && log.getDetail().contains("mismatchType=ACCOUNT_MISMATCH")
    ));
    assertThat(result.getOutcome()).isEqualTo("MISMATCH");
    assertThat(result.getMismatchType()).isEqualTo("ACCOUNT_MISMATCH");
    assertThat(result.getRestored()).isZero();
    assertThat(result.getMismatched()).isEqualTo(1);
    assertThat(result.getFailed()).isZero();
    assertThat(meterRegistry.get("channel.order.idempotency.reconciliation.runs")
        .tag("outcome", "success")
        .counter()
        .count()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("channel.order.idempotency.reconciliation.records")
        .tag("result", "mismatch")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  @Test
  void shouldClassifyTerminalStateMismatchWithoutMutatingCanceledSession() {
    OrderSession session = canceledSession(CL_ORD_ID, "FEP-KRX-" + CL_ORD_ID, "ESCALATED");
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(corebankClient.getOrderSnapshot(eq(CL_ORD_ID), eq(actor.getCorrelationId())))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "FILLED",
            "CONFIRMED",
            "FEP-KRX-" + CL_ORD_ID
        ));

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    verify(orderSessionRepository, never()).findByClOrdIdForUpdate(CL_ORD_ID);
    verify(orderSessionService, never()).reconcileExternalLinkage(any(), any(), any());
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECONCILIATION".equals(log.getAction())
            && log.getDetail().contains("outcome=MISMATCH")
            && log.getDetail().contains("mismatchType=TERMINAL_STATE_MISMATCH")
            && log.getDetail().contains("sessionStatus=CANCELED")
            && log.getDetail().contains("corebankStatus=FILLED")
    ));
    assertThat(result.getOutcome()).isEqualTo("MISMATCH");
    assertThat(result.getMismatchType()).isEqualTo("TERMINAL_STATE_MISMATCH");
    assertThat(result.getRestored()).isZero();
    assertThat(result.getMismatched()).isEqualTo(1);
  }

  @Test
  void shouldCountFailedRunsWhenDownstreamRefreshCannotBeCompleted() {
    OrderSession session = completedSession(CL_ORD_ID, null, "FAILED");
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(corebankClient.getOrderSnapshot(eq(CL_ORD_ID), eq(actor.getCorrelationId())))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "FILLED",
            "FAILED",
            null
        ));
    when(corebankClient.requeryOrder(eq(CL_ORD_ID), eq(1), eq(actor.getCorrelationId())))
        .thenThrow(new BusinessException(
            ErrorCode.FEP_GATEWAY_TIMEOUT,
            ErrorCode.FEP_GATEWAY_TIMEOUT.defaultMessage()
        ));

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    verify(orderSessionService, never()).reconcileExternalLinkage(any(), any(), any());
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECONCILIATION".equals(log.getAction())
            && log.getDetail().contains("outcome=FAILED")
            && log.getDetail().contains("reason=FEP_GATEWAY_TIMEOUT")
    ));
    assertThat(result.getOutcome()).isEqualTo("FAILED");
    assertThat(result.getRestored()).isZero();
    assertThat(result.getMismatched()).isZero();
    assertThat(result.getFailed()).isEqualTo(1);
    assertThat(meterRegistry.get("channel.order.idempotency.reconciliation.runs")
        .tag("outcome", "failed")
        .counter()
        .count()).isEqualTo(1.0d);
    assertThat(meterRegistry.get("channel.order.idempotency.reconciliation.records")
        .tag("result", "failed")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  @Test
  void shouldClassifyStructuredClOrdIdMismatchAsMismatch() {
    OrderSession session = completedSession(CL_ORD_ID, null, "FAILED");
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(corebankClient.getOrderSnapshot(eq(CL_ORD_ID), eq(actor.getCorrelationId())))
        .thenThrow(new BusinessException(
            ErrorCode.CONTRACT_VALIDATION_FAILED,
            "status response clOrdId must match request",
            new ErrorMetadata(null, "DOWNSTREAM_CL_ORD_ID_MISMATCH")
        ));

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    verify(orderSessionService, never()).reconcileExternalLinkage(any(), any(), any());
    assertThat(result.getOutcome()).isEqualTo("MISMATCH");
    assertThat(result.getMismatchType()).isEqualTo("DOWNSTREAM_CL_ORD_ID_MISMATCH");
    assertThat(result.getMismatched()).isEqualTo(1);
  }

  @Test
  void shouldFailWhenRefreshedSnapshotStillLacksConfirmedDownstreamLinkage() {
    OrderSession session = completedSession(CL_ORD_ID, null, "FAILED");
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(corebankClient.getOrderSnapshot(eq(CL_ORD_ID), eq(actor.getCorrelationId())))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "FILLED",
            "FAILED",
            null
        ))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "FILLED",
            "FAILED",
            "FEP-KRX-" + CL_ORD_ID
        ));
    when(corebankClient.requeryOrder(eq(CL_ORD_ID), eq(1), eq(actor.getCorrelationId())))
        .thenReturn(OrderRequeryResult.of(
            9001L,
            CL_ORD_ID,
            "FILLED",
            "FAILED",
            "FILLED",
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.valueOf(72000),
            "FEP-KRX-" + CL_ORD_ID,
            Instant.parse("2026-03-23T02:00:00Z"),
            null,
            "still syncing",
            true,
            false,
            1,
            5
        ));

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    verify(orderSessionRepository, never()).findByClOrdIdForUpdate(CL_ORD_ID);
    verify(orderSessionService, never()).reconcileExternalLinkage(any(), any(), any());
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECONCILIATION".equals(log.getAction())
            && log.getDetail().contains("outcome=FAILED")
            && log.getDetail().contains("reason=DOWNSTREAM_SYNC_UNRESOLVED")
    ));
    assertThat(result.getOutcome()).isEqualTo("FAILED");
    assertThat(result.getExternalOrderId()).isEqualTo("FEP-KRX-" + CL_ORD_ID);
    assertThat(result.getExternalSyncStatus()).isEqualTo("FAILED");
    assertThat(result.getRestored()).isZero();
    assertThat(result.getFailed()).isEqualTo(1);
  }

  @Test
  void shouldRejectPreExecutionSessionBeforeRemoteLookup() {
    OrderSession session = authedSession(CL_ORD_ID);
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    verify(corebankClient, never()).getOrderSnapshot(any(), any());
    verify(orderSessionRepository, never()).findByClOrdIdForUpdate(any());
    verify(orderSessionService, never()).reconcileExternalLinkage(any(), any(), any());
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECONCILIATION".equals(log.getAction())
            && log.getDetail().contains("outcome=FAILED")
            && log.getDetail().contains("reason=SESSION_NOT_EXECUTION_ELIGIBLE")
    ));
    assertThat(result.getOutcome()).isEqualTo("FAILED");
    assertThat(result.getMessage()).isEqualTo("order session is not in a post-execution reconciliation state");
  }

  @Test
  void shouldRecheckEligibilityAfterTakingShortLock() {
    OrderSession session = completedSession(CL_ORD_ID, null, "FAILED");
    OrderSession lockedSession = authedSession(CL_ORD_ID);
    AdminActorContext actor = actor();
    when(orderSessionRepository.findByClOrdId(CL_ORD_ID)).thenReturn(Optional.of(session));
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(Optional.of(lockedSession));
    when(corebankClient.getOrderSnapshot(eq(CL_ORD_ID), eq(actor.getCorrelationId())))
        .thenReturn(CorebankOrderSnapshotResult.of(
            9001L,
            session.getAccountId(),
            CL_ORD_ID,
            "FILLED",
            "CONFIRMED",
            "FEP-KRX-" + CL_ORD_ID
        ));

    var result = reconciliationService.reconcile(CL_ORD_ID, actor);

    verify(orderSessionService, never()).reconcileExternalLinkage(any(), any(), any());
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECONCILIATION".equals(log.getAction())
            && log.getDetail().contains("outcome=FAILED")
            && log.getDetail().contains("reason=SESSION_NOT_EXECUTION_ELIGIBLE")
    ));
    assertThat(result.getOutcome()).isEqualTo("FAILED");
    assertThat(result.getFailed()).isEqualTo(1);
  }

  private AdminActorContext actor() {
    return AdminActorContext.of(
        77L,
        "admin-operator-1",
        "admin@fixyz.com",
        "session-1",
        "127.0.0.1",
        "JUnit",
        "corr-reconcile-1"
    );
  }

  private OrderSession completedSession(String clOrdId, String externalOrderId, String externalSyncStatus) {
    OrderSession session = OrderSession.initiated(
        11L,
        101L,
        clOrdId,
        "fp-" + clOrdId,
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(72000),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.parse("2026-03-23T03:00:00Z")
    );
    session.startExecuting();
    session.complete(
        "FILLED",
        BigDecimal.ONE,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        externalOrderId,
        externalSyncStatus,
        Instant.parse("2026-03-23T02:00:00Z")
    );
    return session;
  }

  private OrderSession authedSession(String clOrdId) {
    return OrderSession.initiated(
        11L,
        101L,
        clOrdId,
        "fp-" + clOrdId,
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(72000),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.parse("2026-03-23T03:00:00Z")
    );
  }

  private OrderSession canceledSession(String clOrdId, String externalOrderId, String externalSyncStatus) {
    OrderSession session = OrderSession.initiated(
        11L,
        101L,
        clOrdId,
        "fp-" + clOrdId,
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(72000),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.parse("2026-03-23T03:00:00Z")
    );
    session.startExecuting();
    session.cancel(
        "CANCELED",
        BigDecimal.ZERO,
        BigDecimal.ONE,
        BigDecimal.valueOf(72000),
        externalOrderId,
        externalSyncStatus,
        Instant.parse("2026-03-23T02:00:00Z"),
        Instant.parse("2026-03-23T02:01:00Z")
    );
    return session;
  }

  private void reserveRecoveryAttempts(OrderSession session, int count) {
    Instant nextAttemptAt = Instant.parse("2026-03-23T04:00:00Z");
    for (int attempt = 0; attempt < count; attempt += 1) {
      session.reserveRecoveryAttempt(nextAttemptAt.plusSeconds(attempt));
    }
  }
}
