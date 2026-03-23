package com.fix.channel.service;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.entity.OrderSession;
import com.fix.common.fep.FepQuoteSourceMode;
import com.fix.channel.vo.OrderExecuteCommand;
import com.fix.channel.vo.OrderExecuteResult;
import com.fix.channel.vo.OrderSessionResult;

@ExtendWith(MockitoExtension.class)
class OrderExecutionServiceTest {

  private FakeCorebankClient corebankClient;

  @Mock
  private OrderSessionService orderSessionService;

  @Mock
  private OrderSessionExecutionLockService orderSessionExecutionLockService;

  @Mock
  private ChannelScaffoldService channelScaffoldService;

  private OrderExecutionService orderExecutionService;

  @BeforeEach
  void setUp() {
    corebankClient = new FakeCorebankClient();
    orderExecutionService = new OrderExecutionService(
        corebankClient,
        orderSessionService,
        orderSessionExecutionLockService,
        channelScaffoldService
    );
  }

  @Test
  void shouldPersistNotificationWhenExecutionCompletes() {
    OrderSession authedSession = createAuthedSession();
    OrderSession completedSession = createAuthedSession();
    when(orderSessionService.requireOwnedSession(1L, authedSession.getOrderSessionId())).thenReturn(authedSession);
    when(orderSessionService.beginExecution(authedSession)).thenReturn(authedSession);
    corebankClient.willReturn(OrderExecuteResult.of(
        1001L,
        authedSession.getClOrdId(),
        "FILLED",
        false,
        BigDecimal.TEN,
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "EXT-1",
        "CONFIRMED",
        Instant.now()
      ));
    when(orderSessionService.completeExecution(
        eq(authedSession),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any()
    ))
        .thenReturn(completedSession);
    when(orderSessionService.toResult(completedSession, false, false)).thenReturn(mockResult());

    orderExecutionService.execute(1L, authedSession.getOrderSessionId());

    verify(channelScaffoldService).bootstrapNotification(
        eq(1L),
        eq("ORDER"),
        startsWith("orderSessionId=" + completedSession.getOrderSessionId() + " status=COMPLETED")
    );
  }

  @Test
  void shouldForwardCanonicalClOrdIdToCorebankCommand() {
    OrderSession authedSession = createAuthedSession();
    OrderSession completedSession = createAuthedSession();
    when(orderSessionService.requireOwnedSession(1L, authedSession.getOrderSessionId())).thenReturn(authedSession);
    when(orderSessionService.beginExecution(authedSession)).thenReturn(authedSession);
    corebankClient.willReturn(OrderExecuteResult.of(
        1004L,
        authedSession.getClOrdId(),
        "FILLED",
        false,
        BigDecimal.TEN,
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "EXT-CLORD",
        "CONFIRMED",
        Instant.parse("2026-03-21T00:00:02Z")
    ));
    when(orderSessionService.completeExecution(
        eq(authedSession),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any()
    )).thenReturn(completedSession);
    when(orderSessionService.toResult(completedSession, false, false)).thenReturn(mockResult());

    orderExecutionService.execute(1L, authedSession.getOrderSessionId());

    assertThat(corebankClient.lastCommand).isNotNull();
    assertThat(corebankClient.lastCommand.getClOrdId())
        .isEqualTo(authedSession.getClOrdId());
  }

  @Test
  void shouldPersistFailedNotificationWhenExecutionThrowsNonEscalationError() {
    OrderSession authedSession = createAuthedSession();
    when(orderSessionService.requireOwnedSession(1L, authedSession.getOrderSessionId())).thenReturn(authedSession);
    when(orderSessionService.beginExecution(authedSession)).thenReturn(authedSession);
    corebankClient.willThrow(new IllegalStateException("boom"));
    when(orderSessionService.markFailed(authedSession, "IllegalStateException")).thenReturn(authedSession);

    assertThatThrownBy(() -> orderExecutionService.execute(1L, authedSession.getOrderSessionId()))
        .isInstanceOf(IllegalStateException.class);

    verify(channelScaffoldService).bootstrapNotification(
        eq(1L),
        eq("ORDER"),
        startsWith("orderSessionId=" + authedSession.getOrderSessionId() + " status=FAILED")
    );
  }

  @Test
  void shouldRouteUnknownExecutionOutcomeIntoRequeryingState() {
    OrderSession authedSession = createAuthedSession();
    OrderSession requeryingSession = createAuthedSession();
    requeryingSession.startExecuting();
    requeryingSession.beginRequerying(
        "UNKNOWN_EXECUTION_OUTCOME",
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "EXT-REQUERY",
        "FAILED",
        Instant.parse("2026-03-18T00:00:00Z")
    );
    when(orderSessionService.requireOwnedSession(1L, authedSession.getOrderSessionId())).thenReturn(authedSession);
    when(orderSessionService.beginExecution(authedSession)).thenReturn(authedSession);
    corebankClient.willReturn(OrderExecuteResult.of(
        1002L,
        authedSession.getClOrdId(),
        "PENDING",
        false,
        BigDecimal.TEN,
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "EXT-REQUERY",
        "FAILED",
        Instant.parse("2026-03-18T00:00:00Z")
    ));
    when(orderSessionService.beginRequerying(
        eq(authedSession),
        eq("UNKNOWN_EXECUTION_OUTCOME"),
        eq("FILLED"),
        eq(BigDecimal.TEN),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("EXT-REQUERY"),
        eq("FAILED"),
        eq(Instant.parse("2026-03-18T00:00:00Z"))
    )).thenReturn(requeryingSession);
    when(orderSessionService.toResult(requeryingSession, false, false)).thenReturn(mockResult());

    orderExecutionService.execute(1L, authedSession.getOrderSessionId());

    verify(orderSessionService).beginRequerying(
        eq(authedSession),
        eq("UNKNOWN_EXECUTION_OUTCOME"),
        eq("FILLED"),
        eq(BigDecimal.TEN),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("EXT-REQUERY"),
        eq("FAILED"),
        eq(Instant.parse("2026-03-18T00:00:00Z"))
    );
    verify(channelScaffoldService, never()).bootstrapNotification(any(), any(), any());
  }

  @Test
  void shouldEscalateWhenCorebankReturnsNonConfirmedNonFailedSyncStatus() {
    OrderSession authedSession = createAuthedSession();
    OrderSession escalatedSession = createAuthedSession();
    escalatedSession.startExecuting();
    escalatedSession.escalate(
        OrderSession.ESCALATED_MANUAL_REVIEW,
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "EXT-ESCALATED",
        "PENDING_CONFIRMATION",
        Instant.parse("2026-03-18T00:01:00Z")
    );
    when(orderSessionService.requireOwnedSession(1L, authedSession.getOrderSessionId())).thenReturn(authedSession);
    when(orderSessionService.beginExecution(authedSession)).thenReturn(authedSession);
    corebankClient.willReturn(OrderExecuteResult.of(
        1005L,
        authedSession.getClOrdId(),
        "PENDING",
        false,
        BigDecimal.TEN,
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "EXT-ESCALATED",
        "PENDING_CONFIRMATION",
        Instant.parse("2026-03-18T00:01:00Z")
    ));
    when(orderSessionService.markEscalated(
        eq(authedSession),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq("FILLED"),
        eq(BigDecimal.TEN),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("EXT-ESCALATED"),
        eq("PENDING_CONFIRMATION"),
        eq(Instant.parse("2026-03-18T00:01:00Z"))
    )).thenReturn(escalatedSession);
    when(orderSessionService.toResult(escalatedSession, false, false)).thenReturn(mockResult());

    orderExecutionService.execute(1L, authedSession.getOrderSessionId());

    verify(orderSessionService).markEscalated(
        eq(authedSession),
        eq(OrderSession.ESCALATED_MANUAL_REVIEW),
        eq("FILLED"),
        eq(BigDecimal.TEN),
        eq(BigDecimal.ZERO),
        eq(BigDecimal.valueOf(72000)),
        eq("EXT-ESCALATED"),
        eq("PENDING_CONFIRMATION"),
        eq(Instant.parse("2026-03-18T00:01:00Z"))
    );
    verify(channelScaffoldService).bootstrapNotification(
        eq(1L),
        eq("ORDER"),
        startsWith("orderSessionId=" + escalatedSession.getOrderSessionId() + " status=ESCALATED")
    );
  }

  @Test
  void shouldForwardMarketQuoteContextFromSession() {
    OrderSession authedSession = OrderSession.initiated(
        1L,
        101L,
        "123e4567-e89b-42d3-a456-426614174261",
        "fingerprint-market",
        "005930",
        "BUY",
        "MARKET",
        BigDecimal.TEN,
        null,
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.now().plusSeconds(300),
        "qsnap-20260321-0001",
        Instant.parse("2026-03-21T00:00:00Z"),
        FepQuoteSourceMode.LIVE,
        BigDecimal.valueOf(72100)
    );
    OrderSession completedSession = createAuthedSession();
    when(orderSessionService.requireOwnedSession(1L, authedSession.getOrderSessionId())).thenReturn(authedSession);
    when(orderSessionService.beginExecution(authedSession)).thenReturn(authedSession);
    corebankClient.willReturn(OrderExecuteResult.of(
        1003L,
        authedSession.getClOrdId(),
        "FILLED",
        false,
        BigDecimal.TEN,
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72100),
        "EXT-MARKET",
        "CONFIRMED",
        Instant.parse("2026-03-21T00:00:01Z")
    ));
    when(orderSessionService.completeExecution(
        eq(authedSession),
        any(),
        any(),
        any(),
        any(),
        any(),
        any(),
        any()
    )).thenReturn(completedSession);
    when(orderSessionService.toResult(completedSession, false, false)).thenReturn(mockResult());

    orderExecutionService.execute(1L, authedSession.getOrderSessionId());

    assertThat(corebankClient.lastCommand).isNotNull();
    assertThat(corebankClient.lastCommand.getOrderType()).isEqualTo("MARKET");
    assertThat(corebankClient.lastCommand.getPrice()).isNull();
    assertThat(corebankClient.lastCommand.getQuoteSnapshotId())
        .isEqualTo("qsnap-20260321-0001");
    assertThat(corebankClient.lastCommand.getQuoteAsOf())
        .isEqualTo(Instant.parse("2026-03-21T00:00:00Z"));
    assertThat(corebankClient.lastCommand.getQuoteSourceMode())
        .isEqualTo(FepQuoteSourceMode.LIVE);
    assertThat(corebankClient.lastCommand.getPreTradePrice())
        .isEqualByComparingTo("72100");
  }

  private OrderSession createAuthedSession() {
    return OrderSession.initiated(
        1L,
        101L,
        "123e4567-e89b-42d3-a456-426614174260",
        "fingerprint",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.now().plusSeconds(300)
    );
  }

  private OrderSessionResult mockResult() {
    return OrderSessionResult.of(
        "123e4567-e89b-42d3-a456-426614174260",
        "123e4567-e89b-42d3-a456-426614174260",
        "COMPLETED",
        false,
        "TRUSTED_AUTH_SESSION",
        101L,
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        null,
        null,
        null,
        null,
        Instant.now().plusSeconds(300),
        120L,
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "EXT-1",
        null,
        null,
        Instant.now(),
        null,
        Instant.now(),
        Instant.now(),
        false
    );
  }

  private static final class FakeCorebankClient extends CorebankClient {

    private OrderExecuteResult nextResult;
    private RuntimeException nextFailure;
    private OrderExecuteCommand lastCommand;

    private FakeCorebankClient() {
      super(RestClient.create(), "test-secret");
    }

    private void willReturn(OrderExecuteResult result) {
      this.nextResult = result;
      this.nextFailure = null;
    }

    private void willThrow(RuntimeException failure) {
      this.nextFailure = failure;
      this.nextResult = null;
    }

    @Override
    public OrderExecuteResult executeOrder(OrderExecuteCommand command, String correlationId) {
      this.lastCommand = command;
      if (nextFailure != null) {
        throw nextFailure;
      }
      return nextResult;
    }
  }
}
