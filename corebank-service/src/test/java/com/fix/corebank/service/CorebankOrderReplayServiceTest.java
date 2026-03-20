package com.fix.corebank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.fep.FepExecType;
import com.fix.common.fep.FepOrdStatus;
import com.fix.corebank.client.FepClient;
import com.fix.corebank.client.FepOrderResult;
import com.fix.corebank.client.FepReplayPayload;
import com.fix.corebank.client.FepReplayResult;
import com.fix.corebank.entity.Order;
import com.fix.corebank.repository.OrderRepository;
import com.fix.corebank.vo.InternalOrderReplayCommand;
import com.fix.corebank.vo.InternalOrderReplayResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CorebankOrderReplayServiceTest {

  private static final String CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174710";
  private static final String OPERATOR_ID = "123e4567-e89b-42d3-a456-426614174701";
  private static final Instant PROCESSED_AT = Instant.parse("2026-03-19T11:30:00Z");

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private FepClient fepClient;

  private CorebankOrderReplayService service;

  @BeforeEach
  void setUp() {
    service = new CorebankOrderReplayService(orderRepository, fepClient);
    ReflectionTestUtils.setField(service, "maxAttempts", 2);
  }

  @Test
  void shouldResolveCompletedReplayAndConfirmOrder() {
    Order order = escalatedOrder();
    when(orderRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(Optional.of(order));
    when(fepClient.replayOrder(anyPayload(), command().getCorrelationId()))
        .thenReturn(new FepReplayResult(
            CL_ORD_ID,
            "COMPLETED",
            null,
            "FILLED",
            10L,
            72000L,
            OPERATOR_ID,
            PROCESSED_AT
        ));
    when(fepClient.queryOrderStatus(CL_ORD_ID, command().getCorrelationId()))
        .thenReturn(new FepOrderResult(
            CL_ORD_ID,
            "FEP-710",
            FepExecType.FILL,
            FepOrdStatus.FILLED,
            10L,
            72000L,
            0L,
            Instant.parse("2026-03-19T11:29:00Z"),
            PROCESSED_AT,
            null,
            null,
            null,
            null
        ));

    InternalOrderReplayResult result = service.replay(command());

    assertThat(result.getFinalStatus()).isEqualTo("COMPLETED");
    assertThat(result.getExecutionResult()).isEqualTo("FILLED");
    assertThat(result.getExecutionSource()).isEqualTo("FILLED");
    assertThat(result.getExecutedQty()).isEqualByComparingTo("10.0000");
    assertThat(order.getStatus()).isEqualTo("FILLED");
    assertThat(order.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_CONFIRMED);
    assertThat(order.getExecutionResult()).isEqualTo("FILLED");
    verify(orderRepository).flush();
  }

  @Test
  void shouldTranslateRetriableReplayFailureToCore001() {
    Order order = escalatedOrder();
    when(orderRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(Optional.of(order));
    when(fepClient.replayOrder(anyPayload(), command().getCorrelationId()))
        .thenThrow(new BusinessException(ErrorCode.FEP_GATEWAY_TIMEOUT, "timeout"))
        .thenThrow(new BusinessException(ErrorCode.FEP_GATEWAY_TIMEOUT, "timeout"));

    assertThatThrownBy(() -> service.replay(command()))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.CORE_PROVISIONING_UNAVAILABLE));

    assertThat(order.getExternalSyncStatus()).isEqualTo(Order.EXTERNAL_SYNC_ESCALATED);
    verify(orderRepository, never()).flush();
  }

  @Test
  void shouldResolvePartialFillCancelReplay() {
    Order order = escalatedOrder();
    when(orderRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(Optional.of(order));
    when(fepClient.replayOrder(anyPayload(), command().getCorrelationId()))
        .thenReturn(new FepReplayResult(
            CL_ORD_ID,
            "CANCELED",
            "PARTIAL_FILL_CANCEL",
            null,
            5L,
            72000L,
            OPERATOR_ID,
            PROCESSED_AT
        ));
    when(fepClient.queryOrderStatus(CL_ORD_ID, command().getCorrelationId()))
        .thenReturn(new FepOrderResult(
            CL_ORD_ID,
            "FEP-710",
            FepExecType.CANCELED,
            FepOrdStatus.CANCELED,
            5L,
            72000L,
            null,
            Instant.parse("2026-03-19T11:28:00Z"),
            PROCESSED_AT,
            null,
            null,
            5L,
            null
        ));

    InternalOrderReplayResult result = service.replay(command());

    assertThat(result.getFinalStatus()).isEqualTo("CANCELED");
    assertThat(result.getExecutionResult()).isEqualTo("PARTIAL_FILL_CANCEL");
    assertThat(result.getExecutedQty()).isEqualByComparingTo("5.0000");
    assertThat(result.getLeavesQty()).isEqualByComparingTo("5.0000");
    assertThat(order.getStatus()).isEqualTo("CANCELED");
    assertThat(order.getExecutionResult()).isEqualTo("PARTIAL_FILL_CANCEL");
    verify(orderRepository).flush();
  }

  private InternalOrderReplayCommand command() {
    return InternalOrderReplayCommand.of(
        CL_ORD_ID,
        "APPROVE",
        OPERATOR_ID,
        "123e4567-e89b-42d3-a456-426614174799",
        "OPS-INC-710",
        "KRX outage resolved after manual exchange confirmation",
        72000L,
        "corr-replay-710"
    );
  }

  private FepReplayPayload anyPayload() {
    return new FepReplayPayload(
        CL_ORD_ID,
        "APPROVE",
        OPERATOR_ID,
        "123e4567-e89b-42d3-a456-426614174799",
        "OPS-INC-710",
        "KRX outage resolved after manual exchange confirmation",
        72000L
    );
  }

  private Order escalatedOrder() {
    Order order = Order.accepted(
        1L,
        CL_ORD_ID,
        "005930",
        "BUY",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000)
    );
    order.completeExecution(
        "ACCEPTED",
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        Instant.parse("2026-03-19T11:00:00Z")
    );
    order.updateState("ACCEPTED", Order.EXTERNAL_SYNC_ESCALATED, "FEP-710", "ESCALATED_MANUAL_REVIEW");
    return order;
  }
}
