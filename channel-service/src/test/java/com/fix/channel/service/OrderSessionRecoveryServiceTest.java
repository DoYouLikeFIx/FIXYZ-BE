package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
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

@ExtendWith(MockitoExtension.class)
class OrderSessionRecoveryServiceTest {

  @Mock
  private OrderSessionService orderSessionService;
  @Mock
  private CorebankClient corebankClient;
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
        manualRecoveryQueueService,
        recoveryLockService,
        attemptStore,
        Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC),
        meterRegistry,
        100,
        Duration.ofSeconds(30)
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
    when(orderSessionService.findRequeryingSessions(100)).thenReturn(List.of(requerying));
    when(recoveryLockService.tryAcquire(requerying.getOrderSessionId())).thenReturn(true);
    when(attemptStore.nextAttempt(requerying.getOrderSessionId())).thenReturn(1);
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
            "UNKNOWN",
            true,
            false,
            1,
            5
        ));

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).beginRequerying(eq(executing), eq("EXECUTING_TIMEOUT"));
    verify(corebankClient).requeryOrder(eq(requerying.getClOrdId()), eq(1), any(String.class));
  }

  @Test
  void shouldConvergeToCompletedWhenRequeryReturnsFilled() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174402");
    when(orderSessionService.findTimedOutExecutingSessions(any(Instant.class), eq(100))).thenReturn(List.of());
    when(orderSessionService.findRequeryingSessions(100)).thenReturn(List.of(requerying));
    when(recoveryLockService.tryAcquire(requerying.getOrderSessionId())).thenReturn(true);
    when(attemptStore.nextAttempt(requerying.getOrderSessionId())).thenReturn(2);
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
            Instant.parse("2026-03-18T00:00:00Z"),
            null,
            false,
            false,
            2,
            5
        ));

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).completeExecution(
        eq(requerying),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("FEP-402"),
        eq("CONFIRMED"),
        eq(Instant.parse("2026-03-18T00:00:00Z"))
    );
    verify(attemptStore).clear(requerying.getOrderSessionId());
    assertThat(meterRegistry.get("channel.order.recovery.convergence")
        .tag("outcome", "success")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  @Test
  void shouldConvergeToCompletedWhenRequeryReturnsAccepted() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174412");
    when(orderSessionService.findTimedOutExecutingSessions(any(Instant.class), eq(100))).thenReturn(List.of());
    when(orderSessionService.findRequeryingSessions(100)).thenReturn(List.of(requerying));
    when(recoveryLockService.tryAcquire(requerying.getOrderSessionId())).thenReturn(true);
    when(attemptStore.nextAttempt(requerying.getOrderSessionId())).thenReturn(1);
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
            Instant.parse("2026-03-18T00:00:00Z"),
            null,
            false,
            false,
            1,
            5
        ));

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).completeExecution(
        eq(requerying),
        eq("FILLED"),
        eq(BigDecimal.ONE),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(71000)),
        eq("FEP-412"),
        eq("CONFIRMED"),
        eq(Instant.parse("2026-03-18T00:00:00Z"))
    );
  }

  @Test
  void shouldEscalateWhenRequeryThresholdIsExceeded() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174403");
    when(orderSessionService.findTimedOutExecutingSessions(any(Instant.class), eq(100))).thenReturn(List.of());
    when(orderSessionService.findRequeryingSessions(100)).thenReturn(List.of(requerying));
    when(recoveryLockService.tryAcquire(requerying.getOrderSessionId())).thenReturn(true);
    when(attemptStore.nextAttempt(requerying.getOrderSessionId())).thenReturn(5);
    when(corebankClient.requeryOrder(eq(requerying.getClOrdId()), eq(5), any(String.class)))
        .thenReturn(OrderRequeryResult.of(
            3L,
            requerying.getClOrdId(),
            "UNKNOWN",
            "ESCALATED",
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

    recoveryService.runRecoveryCycle();

    verify(orderSessionService).markEscalated(
        eq(requerying),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq(null),
        eq("ESCALATED"),
        eq(null)
    );
    verify(manualRecoveryQueueService).enqueue(
        eq(requerying.getOrderSessionId()),
        eq(requerying.getClOrdId()),
        eq(5),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW)
    );
    verify(attemptStore).clear(requerying.getOrderSessionId());
    assertThat(meterRegistry.get("channel.order.recovery.convergence")
        .tag("outcome", "escalated")
        .counter()
        .count()).isEqualTo(1.0d);
  }

  @Test
  void shouldRecordAttemptMetricForEachRequery() {
    OrderSession requerying = requeryingSession("123e4567-e89b-42d3-a456-426614174404");
    when(orderSessionService.findTimedOutExecutingSessions(any(Instant.class), eq(100))).thenReturn(List.of());
    when(orderSessionService.findRequeryingSessions(100)).thenReturn(List.of(requerying));
    when(recoveryLockService.tryAcquire(requerying.getOrderSessionId())).thenReturn(true);
    when(attemptStore.nextAttempt(requerying.getOrderSessionId())).thenReturn(1);
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
  void shouldNotLoopIndefinitelyWhenRequeryingBatchDoesNotConverge() {
    OrderSession first = requeryingSession("123e4567-e89b-42d3-a456-426614174421");
    OrderSession second = requeryingSession("123e4567-e89b-42d3-a456-426614174422");
    when(orderSessionService.findTimedOutExecutingSessions(any(Instant.class), eq(2))).thenReturn(List.of());
    when(orderSessionService.findRequeryingSessions(2))
        .thenReturn(List.of(first, second))
        .thenReturn(List.of(first, second));
    when(recoveryLockService.tryAcquire(any(String.class))).thenReturn(true);
    when(attemptStore.nextAttempt(any(String.class))).thenReturn(1);
    when(corebankClient.requeryOrder(any(String.class), eq(1), any(String.class)))
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
            "still pending",
            true,
            false,
            1,
            5
        ));

    OrderSessionRecoveryService smallBatchRecoveryService = new OrderSessionRecoveryService(
        orderSessionService,
        corebankClient,
        manualRecoveryQueueService,
        recoveryLockService,
        attemptStore,
        Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC),
        meterRegistry,
        2,
        Duration.ofSeconds(30)
    );
    smallBatchRecoveryService.runRecoveryCycle();

    verify(orderSessionService, times(2)).findRequeryingSessions(2);
    verify(corebankClient, times(2)).requeryOrder(any(String.class), eq(1), any(String.class));
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
}
