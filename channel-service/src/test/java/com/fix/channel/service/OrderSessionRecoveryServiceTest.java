package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.entity.OrderSession;
import com.fix.channel.vo.OrderRequeryResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderSessionRecoveryServiceTest {

  private static final Instant NOW = Instant.parse("2026-03-18T00:00:00Z");

  @Mock
  private OrderSessionService orderSessionService;
  @Mock
  private CorebankClient corebankClient;
  @Mock
  private ChannelScaffoldService channelScaffoldService;
  @Mock
  private AuditLogService auditLogService;
  @Mock
  private ManualRecoveryQueueService manualRecoveryQueueService;
  @Mock
  private OrderSessionRecoveryLockService recoveryLockService;
  @Mock
  private OrderSessionRecoveryAttemptStore attemptStore;

  private SimpleMeterRegistry meterRegistry;
  private OrderSessionRecoveryService recoveryService;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    recoveryService = new OrderSessionRecoveryService(
        orderSessionService,
        corebankClient,
        channelScaffoldService,
        auditLogService,
        manualRecoveryQueueService,
        recoveryLockService,
        attemptStore,
        Clock.fixed(NOW, ZoneOffset.UTC),
        meterRegistry,
        100,
        Duration.ofSeconds(30),
        5
    );
  }

  @Test
  void shouldTransitionTimedOutExecutingSessionsAndRunRequery() {
    OrderSession executing = executingSession("123e4567-e89b-42d3-a456-426614174401");
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174401");
    when(orderSessionService.findTimedOutExecutingSessions(any(Instant.class), eq(100)))
        .thenReturn(List.of(executing));
    when(orderSessionService.beginRequerying(eq(executing), eq("EXECUTING_TIMEOUT")))
        .thenReturn(requerying);
    when(orderSessionService.findRequeryingSessionsAfter(eq(NOW), isNull(), isNull(), eq(100)))
        .thenReturn(List.of(requerying));
    when(recoveryLockService.tryAcquire(requerying.getOrderSessionId())).thenReturn("lock-401");
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(1));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(1), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            1L,
            requerying.getClOrdId(),
            "UNKNOWN",
            "FAILED",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "UNKNOWN",
            true,
            false,
            1,
            5
        ));

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).beginRequerying(eq(executing), eq("EXECUTING_TIMEOUT"));
    verify(corebankClient).requeryOrder(eq(requerying.getClOrdId()), eq(1), any(String.class));
    verify(manualRecoveryQueueService).publishPendingEntries();
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECOVERY_ATTEMPT".equals(log.getAction())
            && requerying.getOrderSessionId().equals(log.getTargetId())
            && log.getDetail().contains("attemptCount=1")
            && log.getDetail().contains("outcome=RETRY_PENDING")
            && log.getDetail().contains("recoveryStatus=UNKNOWN")
    ));
    verify(channelScaffoldService, never()).bootstrapNotification(any(), any(), any());
  }

  @Test
  void shouldConvergeToCompletedWhenRequeryReturnsFilled() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174402");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(2));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(2), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            2L,
            requerying.getClOrdId(),
            "FILLED",
            "CONFIRMED",
            "FILLED",
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.valueOf(72000),
            "FEP-402",
            NOW,
            null,
            null,
            false,
            false,
            2,
            5
        ));
    when(orderSessionService.completeExecution(
        eq(requerying),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("FEP-402"),
        eq("CONFIRMED"),
        eq(NOW)
    )).thenReturn(requerying);

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).completeExecution(
        eq(requerying),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("FEP-402"),
        eq("CONFIRMED"),
        eq(NOW)
    );
    verify(attemptStore).clear(requerying.getOrderSessionId());
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECOVERY_ATTEMPT".equals(log.getAction())
            && requerying.getOrderSessionId().equals(log.getTargetId())
            && log.getDetail().contains("attemptCount=2")
            && log.getDetail().contains("outcome=COMPLETED")
            && log.getDetail().contains("recoveryStatus=FILLED")
    ));
    verify(channelScaffoldService).bootstrapNotification(
        eq(requerying.getMemberId()),
        eq("ORDER"),
        eq("orderSessionId=" + requerying.getOrderSessionId() + " status=COMPLETED")
    );
    assertThat(meterRegistry.get("channel.order.recovery.convergence")
        .tag("outcome", "success")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  @Test
  void shouldPublishTerminalNotificationEvenWhenRecoveryAuditWriteFails() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174499");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(1));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(1), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            99L,
            requerying.getClOrdId(),
            "FILLED",
            "CONFIRMED",
            "FILLED",
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.valueOf(72000),
            "FEP-499",
            NOW,
            null,
            null,
            false,
            false,
            1,
            5
        ));
    when(orderSessionService.completeExecution(
        eq(requerying),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("FEP-499"),
        eq("CONFIRMED"),
        eq(NOW)
    )).thenReturn(requerying);
    doThrow(new IllegalStateException("audit unavailable")).when(auditLogService).record(any());

    recoveryService.runRecoveryCycle();

    verify(channelScaffoldService).bootstrapNotification(
        eq(requerying.getMemberId()),
        eq("ORDER"),
        eq("orderSessionId=" + requerying.getOrderSessionId() + " status=COMPLETED")
    );
    verify(attemptStore).clear(requerying.getOrderSessionId());
  }

  @Test
  void shouldConvergeToCompletedWhenRequeryReturnsAccepted() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174412");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(1));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(1), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            12L,
            requerying.getClOrdId(),
            "ACCEPTED",
            "CONFIRMED",
            "FILLED",
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.valueOf(71000),
            "FEP-412",
            NOW,
            null,
            null,
            false,
            false,
            1,
            5
        ));
    when(orderSessionService.completeExecution(
        eq(requerying),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(71000)),
        eq("FEP-412"),
        eq("CONFIRMED"),
        eq(NOW)
    )).thenReturn(requerying);

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).completeExecution(
        eq(requerying),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(71000)),
        eq("FEP-412"),
        eq("CONFIRMED"),
        eq(NOW)
    );
  }

  @Test
  void shouldEscalateUnknownWhenRetryLimitIsReachedEvenWithoutEscalationFlag() {
    assertRetryLimitEscalation("UNKNOWN", "ESCALATED");
  }

  @Test
  void shouldEscalatePendingWhenRetryLimitIsReachedEvenWithoutEscalationFlag() {
    assertRetryLimitEscalation("PENDING", "FAILED");
  }

  @Test
  void shouldEscalateMalformedWhenRetryLimitIsReachedEvenWithoutEscalationFlag() {
    assertRetryLimitEscalation("MALFORMED", "FAILED");
  }

  @Test
  void shouldUseLocalDefaultRetryLimitWhenRequeryResponseOmitsMaxRetryCount() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174426");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(5));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(5), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            16L,
            requerying.getClOrdId(),
            "UNKNOWN",
            "FAILED",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "retry exhausted",
            false,
            false,
            5,
            null
        ));
    when(orderSessionService.markEscalatedAndEnqueueManualRecovery(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq("FAILED"),
        eq(null),
        eq(5)
    )).thenReturn(requerying);

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).markEscalatedAndEnqueueManualRecovery(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq("FAILED"),
        eq(null),
        eq(5)
    );
  }

  @Test
  void shouldConvergeToCompletedWhenRequeryReturnsPartiallyFilled() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174413");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(3));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(3), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            13L,
            requerying.getClOrdId(),
            "PARTIALLY_FILLED",
            "CONFIRMED",
            "PARTIAL_FILL",
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(71000),
            "FEP-413",
            NOW,
            null,
            null,
            false,
            false,
            3,
            5
        ));
    when(orderSessionService.completeExecution(
        eq(requerying),
        eq("PARTIAL_FILL"),
        eq(BigDecimal.valueOf(5)),
        eq(BigDecimal.valueOf(5)),
        eq(BigDecimal.valueOf(71000)),
        eq("FEP-413"),
        eq("CONFIRMED"),
        eq(NOW)
    )).thenReturn(requerying);

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).completeExecution(
        eq(requerying),
        eq("PARTIAL_FILL"),
        eq(BigDecimal.valueOf(5)),
        eq(BigDecimal.valueOf(5)),
        eq(BigDecimal.valueOf(71000)),
        eq("FEP-413"),
        eq("CONFIRMED"),
        eq(NOW)
    );
  }

  @Test
  void shouldConvergeToCanceledWhenRequeryReturnsCanceled() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174415");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(2));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(2), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            15L,
            requerying.getClOrdId(),
            "CANCELED",
            "CONFIRMED",
            "PARTIAL_FILL_CANCEL",
            BigDecimal.valueOf(3),
            BigDecimal.valueOf(7),
            BigDecimal.valueOf(72000),
            "FEP-415",
            Instant.parse("2026-03-18T00:05:00Z"),
            Instant.parse("2026-03-18T00:06:00Z"),
            null,
            false,
            false,
            2,
            5
        ));
    when(orderSessionService.cancelExecution(
        eq(requerying),
        eq("PARTIAL_FILL_CANCEL"),
        eq(BigDecimal.valueOf(3)),
        eq(BigDecimal.valueOf(7)),
        eq(BigDecimal.valueOf(72000)),
        eq("FEP-415"),
        eq("CONFIRMED"),
        eq(Instant.parse("2026-03-18T00:05:00Z")),
        eq(Instant.parse("2026-03-18T00:06:00Z"))
    )).thenReturn(requerying);

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).cancelExecution(
        eq(requerying),
        eq("PARTIAL_FILL_CANCEL"),
        eq(BigDecimal.valueOf(3)),
        eq(BigDecimal.valueOf(7)),
        eq(BigDecimal.valueOf(72000)),
        eq("FEP-415"),
        eq("CONFIRMED"),
        eq(Instant.parse("2026-03-18T00:05:00Z")),
        eq(Instant.parse("2026-03-18T00:06:00Z"))
    );
    verify(attemptStore).clear(requerying.getOrderSessionId());
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECOVERY_ATTEMPT".equals(log.getAction())
            && requerying.getOrderSessionId().equals(log.getTargetId())
            && log.getDetail().contains("attemptCount=2")
            && log.getDetail().contains("outcome=CANCELED")
            && log.getDetail().contains("recoveryStatus=CANCELED")
    ));
    verify(channelScaffoldService).bootstrapNotification(
        eq(requerying.getMemberId()),
        eq("ORDER"),
        eq("orderSessionId=" + requerying.getOrderSessionId() + " status=CANCELED")
    );
    assertThat(meterRegistry.get("channel.order.recovery.convergence")
        .tag("outcome", "success")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  @Test
  void shouldEscalateRejectedRequeryStatusEvenWithoutEscalationFlag() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174414");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(1));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(1), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            14L,
            requerying.getClOrdId(),
            "REJECTED",
            "REJECTED",
            "DECLINED",
            null,
            null,
            null,
            null,
            null,
            null,
            "order rejected",
            false,
            false,
            1,
            5
        ));
    when(orderSessionService.markEscalatedAndEnqueueManualRecovery(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq("DECLINED"),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq("REJECTED"),
        eq(null),
        eq(1)
    )).thenReturn(requerying);

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).markEscalatedAndEnqueueManualRecovery(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq("DECLINED"),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq("REJECTED"),
        eq(null),
        eq(1)
    );
    verify(attemptStore).clear(requerying.getOrderSessionId());
    verify(channelScaffoldService).bootstrapNotification(
        eq(requerying.getMemberId()),
        eq("ORDER"),
        eq("orderSessionId=" + requerying.getOrderSessionId() + " status=ESCALATED")
    );
    assertThat(meterRegistry.get("channel.order.recovery.convergence")
        .tag("outcome", "escalated")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  @Test
  void shouldRecordAttemptMetricForEachRequery() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174404");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(1));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(1), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            4L,
            requerying.getClOrdId(),
            "ACCEPTED",
            "FAILED",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "still pending",
            true,
            false,
            1,
            5
        ));

    recoveryService.runRecoveryCycle();

    assertThat(meterRegistry.get("channel.order.recovery.requery.attempts")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  @Test
  void shouldContinueScanningLaterRequeryingBatchesWithoutStarvingBacklog() {
    OrderSession first = requeryingSession("123e4567-e89b-42d3-a456-426614174421");
    OrderSession second = requeryingSession("123e4567-e89b-42d3-a456-426614174422");
    OrderSession third = requeryingSession("123e4567-e89b-42d3-a456-426614174423");
    setUpdatedAt(first, Instant.parse("2026-03-18T00:01:00Z"));
    setUpdatedAt(second, Instant.parse("2026-03-18T00:02:00Z"));
    setUpdatedAt(third, Instant.parse("2026-03-18T00:03:00Z"));
    when(orderSessionService.findTimedOutExecutingSessions(any(Instant.class), eq(2))).thenReturn(List.of());
    when(orderSessionService.findRequeryingSessionsAfter(eq(NOW), isNull(), isNull(), eq(2)))
        .thenReturn(List.of(first, second));
    when(orderSessionService.findRequeryingSessionsAfter(
        eq(NOW),
        eq(second.getUpdatedAt()),
        eq(second.getOrderSessionId()),
        eq(2)
    )).thenReturn(List.of(third));
    when(recoveryLockService.tryAcquire(anyString())).thenReturn("lock-batch");
    when(attemptStore.reserveAttempt(anyString())).thenReturn(reservation(1));
    when(corebankClient.requeryOrder(anyString(), eq(1), anyString()))
        .thenReturn(OrderRequeryResult.of(
            5L,
            first.getClOrdId(),
            "UNKNOWN",
            "FAILED",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "still pending",
            true,
            false,
            1,
            5
        ));

    OrderSessionRecoveryService smallBatchRecoveryService = new OrderSessionRecoveryService(
        orderSessionService,
        corebankClient,
        channelScaffoldService,
        auditLogService,
        manualRecoveryQueueService,
        recoveryLockService,
        attemptStore,
        Clock.fixed(NOW, ZoneOffset.UTC),
        meterRegistry,
        2,
        Duration.ofSeconds(30),
        5
    );

    smallBatchRecoveryService.runRecoveryCycle();

    verify(orderSessionService, times(2)).findRequeryingSessionsAfter(any(), any(), any(), eq(2));
    verify(corebankClient, times(3)).requeryOrder(anyString(), eq(1), anyString());
  }

  @Test
  void shouldPreserveExistingExecutionSnapshotWhenEscalatingUnknownRecoveryOutcome() {
    OrderSession requerying = requeryingSessionWithSnapshot("123e4567-e89b-42d3-a456-426614174424");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(5));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(5), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            6L,
            requerying.getClOrdId(),
            "UNKNOWN",
            "ESCALATED",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "retry exhausted",
            false,
            true,
            5,
            5
        ));
    when(orderSessionService.markEscalatedAndEnqueueManualRecovery(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("FEP-424"),
        eq("ESCALATED"),
        eq(NOW),
        eq(5)
    )).thenReturn(requerying);

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).markEscalatedAndEnqueueManualRecovery(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("FEP-424"),
        eq("ESCALATED"),
        eq(NOW),
        eq(5)
    );
  }

  @Test
  void shouldEscalateWhenRepeatedRequeryFailuresExhaustRetryBudget() {
    OrderSession requerying = requeryingSessionWithSnapshot("123e4567-e89b-42d3-a456-426614174425");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(5));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(5), any(String.class)))
        .thenThrow(new IllegalStateException("corebank unavailable"));
    when(orderSessionService.markEscalatedAndEnqueueManualRecovery(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("FEP-424"),
        eq("FAILED"),
        eq(NOW),
        eq(5)
    )).thenReturn(requerying);

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).markEscalatedAndEnqueueManualRecovery(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("FEP-424"),
        eq("FAILED"),
        eq(NOW),
        eq(5)
    );
    verify(attemptStore).clear(requerying.getOrderSessionId());
    verify(auditLogService).record(argThat(log ->
        "ORDER_SESSION_RECOVERY_ATTEMPT".equals(log.getAction())
            && requerying.getOrderSessionId().equals(log.getTargetId())
            && log.getDetail().contains("attemptCount=5")
            && log.getDetail().contains("outcome=ESCALATED")
            && log.getDetail().contains("IllegalStateException: corebank unavailable")
    ));
    verify(channelScaffoldService).bootstrapNotification(
        eq(requerying.getMemberId()),
        eq("ORDER"),
        eq("orderSessionId=" + requerying.getOrderSessionId() + " status=ESCALATED")
    );
    assertThat(meterRegistry.get("channel.order.recovery.convergence")
        .tag("outcome", "escalated")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  private void assertRetryLimitEscalation(String status, String externalSyncStatus) {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174403");
    stubSingleRequeryingSession(requerying);
    when(attemptStore.reserveAttempt(requerying.getOrderSessionId())).thenReturn(reservation(5));
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(5), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            3L,
            requerying.getClOrdId(),
            status,
            externalSyncStatus,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "retry exhausted",
            false,
            false,
            5,
            5
        ));
    when(orderSessionService.markEscalatedAndEnqueueManualRecovery(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(externalSyncStatus),
        eq(null),
        eq(5)
    )).thenReturn(requerying);

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).markEscalatedAndEnqueueManualRecovery(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(externalSyncStatus),
        eq(null),
        eq(5)
    );
    verify(attemptStore).clear(requerying.getOrderSessionId());
    verify(channelScaffoldService).bootstrapNotification(
        eq(requerying.getMemberId()),
        eq("ORDER"),
        eq("orderSessionId=" + requerying.getOrderSessionId() + " status=ESCALATED")
    );
    assertThat(meterRegistry.get("channel.order.recovery.convergence")
        .tag("outcome", "escalated")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  private void stubSingleRequeryingSession(OrderSession requerying) {
    when(orderSessionService.findTimedOutExecutingSessions(any(Instant.class), eq(100))).thenReturn(List.of());
    when(orderSessionService.findRequeryingSessionsAfter(eq(NOW), isNull(), isNull(), eq(100)))
        .thenReturn(List.of(requerying));
    when(recoveryLockService.tryAcquire(requerying.getOrderSessionId()))
        .thenReturn("lock-" + requerying.getOrderSessionId());
  }

  private OrderSessionRecoveryAttemptStore.AttemptReservation reservation(int attemptCount) {
    return new OrderSessionRecoveryAttemptStore.AttemptReservation(
        attemptCount,
        NOW.plusSeconds(Math.max(1, attemptCount) * 60L)
    );
  }

  private OrderSession executingSession(String clOrdId) {
    OrderSession session = OrderSession.initiated(
        1L,
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
        Instant.parse("2026-03-18T01:00:00Z")
    );
    session.startExecuting();
    return session;
  }

  private OrderSession requeryingSession(String clOrdId) {
    OrderSession session = executingSession(clOrdId);
    session.beginRequerying("EXECUTING_TIMEOUT");
    return session;
  }

  private OrderSession requeryingSessionWithSnapshot(String clOrdId) {
    OrderSession session = executingSession(clOrdId);
    session.beginRequerying(
        "UNKNOWN_EXECUTION_OUTCOME",
        "FILLED",
        BigDecimal.ONE,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "FEP-424",
        "FAILED",
        NOW
    );
    return session;
  }

  private void setUpdatedAt(OrderSession session, Instant updatedAt) {
    ReflectionTestUtils.setField(session, "updatedAt", updatedAt);
  }
}
