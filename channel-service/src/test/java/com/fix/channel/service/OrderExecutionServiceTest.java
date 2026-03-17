package com.fix.channel.service;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import com.fix.channel.client.CorebankClient;
import com.fix.channel.entity.OrderSession;
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
    when(orderSessionService.completeExecution(eq(authedSession), any(), any(), any(), any(), any(), any()))
        .thenReturn(completedSession);
    when(orderSessionService.toResult(completedSession, false)).thenReturn(mockResult());

    orderExecutionService.execute(1L, authedSession.getOrderSessionId());

    verify(channelScaffoldService).bootstrapNotification(
        eq(1L),
        eq("ORDER"),
        startsWith("orderSessionId=" + completedSession.getOrderSessionId() + " status=COMPLETED")
    );
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
      if (nextFailure != null) {
        throw nextFailure;
      }
      return nextResult;
    }
  }
}