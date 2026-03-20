package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fix.channel.client.CorebankClient;
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
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminOrderReplayServiceTest {

  private static final String CL_ORD_ID = "123e4567-e89b-42d3-a456-426614174510";
  private static final String OPERATOR_ID = "123e4567-e89b-42d3-a456-426614174501";
  private static final String APPROVED_BY = "123e4567-e89b-42d3-a456-426614174599";
  private static final Instant PROCESSED_AT = Instant.parse("2026-03-19T10:15:30Z");

  @Mock
  private OrderSessionRepository orderSessionRepository;

  @Mock
  private CorebankClient corebankClient;

  @Mock
  private AuditLogService auditLogService;

  @Mock
  private ChannelScaffoldService channelScaffoldService;

  private AdminOrderReplayService service;

  @BeforeEach
  void setUp() {
    service = new AdminOrderReplayService(
        orderSessionRepository,
        corebankClient,
        auditLogService,
        channelScaffoldService
    );
  }

  @Test
  void shouldReturnStoredResultForSameReplayFingerprintWithoutCallingCorebank() {
    OrderSession session = escalatedSession();
    session.complete(
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "FEP-510",
        "CONFIRMED",
        Instant.parse("2026-03-19T10:14:00Z")
    );
    String fingerprint = ManualReplayIdentitySupport.replayFingerprint(
        CL_ORD_ID,
        "APPROVE",
        APPROVED_BY,
        "OPS-INC-20260319-1",
        longReason(),
        72000L,
        OPERATOR_ID
    );
    session.recordManualReplayOutcome(fingerprint, OPERATOR_ID, "FILLED", PROCESSED_AT);

    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(java.util.Optional.of(session));

    AdminOrderReplayResult result = service.replay(CL_ORD_ID, replayCommand(), actor());

    assertThat(result.getFinalStatus()).isEqualTo("COMPLETED");
    assertThat(result.getExecutionSource()).isEqualTo("FILLED");
    assertThat(result.getProcessedBy()).isEqualTo(OPERATOR_ID);
    assertThat(result.getProcessedAt()).isEqualTo(PROCESSED_AT);
    verify(corebankClient, never()).replayOrder(any(), any(), any(), any());
    verify(auditLogService, never()).record(any());
  }

  @Test
  void shouldRejectTerminalReplayWhenFingerprintDiffers() {
    OrderSession session = escalatedSession();
    session.fail("MANUAL_REJECT");
    session.recordManualReplayOutcome("different-fingerprint", OPERATOR_ID, null, PROCESSED_AT);

    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(java.util.Optional.of(session));

    assertThatThrownBy(() -> service.replay(CL_ORD_ID, replayCommand(), actor()))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED);
        });

    verify(corebankClient, never()).replayOrder(any(), any(), any(), any());
  }

  @Test
  void shouldKeepEscalatedStateWhenCorebankReplayFailsWithCore001() {
    OrderSession session = escalatedSession();
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(java.util.Optional.of(session));
    when(corebankClient.replayOrder(eq(CL_ORD_ID), any(AdminOrderReplayCommand.class), eq(OPERATOR_ID), eq("corr-admin-replay")))
        .thenThrow(new BusinessException(ErrorCode.CORE_PROVISIONING_UNAVAILABLE, "Corebank provisioning unavailable"));

    assertThatThrownBy(() -> service.replay(CL_ORD_ID, replayCommand(), actor()))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> {
          BusinessException businessException = (BusinessException) ex;
          assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CORE_PROVISIONING_UNAVAILABLE);
        });

    assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.ESCALATED);
    assertThat(session.getManualReplayFingerprint()).isNull();
    verify(orderSessionRepository, never()).flush();
    verify(auditLogService, never()).record(any());
  }

  @Test
  void shouldApplySuccessfulReplayAndRecordEvidence() {
    OrderSession session = escalatedSession();
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(java.util.Optional.of(session));
    when(corebankClient.replayOrder(eq(CL_ORD_ID), any(AdminOrderReplayCommand.class), eq(OPERATOR_ID), eq("corr-admin-replay")))
        .thenReturn(OrderReplayResult.of(
            CL_ORD_ID,
            "COMPLETED",
            "FILLED",
            "VIRTUAL_FILL",
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.valueOf(72000),
            null,
            "CONFIRMED",
            Instant.parse("2026-03-19T10:14:00Z"),
            null,
            OPERATOR_ID,
            PROCESSED_AT
        ));

    AdminOrderReplayResult result = service.replay(CL_ORD_ID, replayCommand(), actor());

    assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.COMPLETED);
    assertThat(session.getManualReplayProcessedBy()).isEqualTo(OPERATOR_ID);
    assertThat(session.getManualReplayExecutionSource()).isEqualTo("VIRTUAL_FILL");
    assertThat(result.getExecutionSource()).isEqualTo("VIRTUAL_FILL");
    verify(orderSessionRepository).flush();
    verify(auditLogService).record(any());
    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(channelScaffoldService).bootstrapTypedNotification(
        eq(session.getMemberId()),
        eq("ORDER"),
        eq("orderSessionId=" + session.getOrderSessionId()
            + " event=ORDER_FILLED status=COMPLETED executionResult=FILLED executionSource=VIRTUAL_FILL"
            + " executedQty=10 leavesQty=0 executedPrice=72000 executedAt=2026-03-19T10:14:00Z"),
        eq("ORDER_FILLED"),
        payloadCaptor.capture()
    );
    assertThat(payloadCaptor.getValue())
        .isEqualTo(Map.ofEntries(
            Map.entry("type", "ORDER_FILLED"),
            Map.entry("orderSessionId", session.getOrderSessionId()),
            Map.entry("clOrdId", CL_ORD_ID),
            Map.entry("symbol", "005930"),
            Map.entry("side", "BUY"),
            Map.entry("executionResult", "FILLED"),
            Map.entry("executedQty", BigDecimal.TEN),
            Map.entry("leavesQty", BigDecimal.ZERO),
            Map.entry("executedPrice", BigDecimal.valueOf(72000)),
            Map.entry("executedAt", Instant.parse("2026-03-19T10:14:00Z")),
            Map.entry("timestamp", PROCESSED_AT)
        ));
  }

  @Test
  void shouldOnlyRecordManualInputForMarketVirtualFill() {
    OrderSession session = marketEscalatedSession();
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(java.util.Optional.of(session));
    when(corebankClient.replayOrder(eq(CL_ORD_ID), any(AdminOrderReplayCommand.class), eq(OPERATOR_ID), eq("corr-admin-replay")))
        .thenReturn(OrderReplayResult.of(
            CL_ORD_ID,
            "COMPLETED",
            "FILLED",
            "VIRTUAL_FILL",
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.valueOf(72000),
            null,
            "CONFIRMED",
            Instant.parse("2026-03-19T10:14:00Z"),
            null,
            OPERATOR_ID,
            PROCESSED_AT
        ));

    service.replay(CL_ORD_ID, replayCommand(), actor());

    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).record(auditLogCaptor.capture());
    assertThat(auditLogCaptor.getValue().getDetail()).contains("executionPriceSource=MANUAL_INPUT");
  }

  @Test
  void shouldNotRecordManualInputWhenFilledExecutionDataWins() {
    OrderSession session = escalatedSession();
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(java.util.Optional.of(session));
    when(corebankClient.replayOrder(eq(CL_ORD_ID), any(AdminOrderReplayCommand.class), eq(OPERATOR_ID), eq("corr-admin-replay")))
        .thenReturn(OrderReplayResult.of(
            CL_ORD_ID,
            "COMPLETED",
            "FILLED",
            "FILLED",
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.valueOf(72000),
            "FEP-510",
            "CONFIRMED",
            Instant.parse("2026-03-19T10:14:00Z"),
            null,
            OPERATOR_ID,
            PROCESSED_AT
        ));

    service.replay(CL_ORD_ID, replayCommand(), actor());

    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).record(auditLogCaptor.capture());
    assertThat(auditLogCaptor.getValue().getDetail()).doesNotContain("executionPriceSource=MANUAL_INPUT");
  }

  @Test
  void shouldPublishFailedReplayNotificationWithFailureReason() {
    OrderSession session = escalatedSession();
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(java.util.Optional.of(session));
    when(corebankClient.replayOrder(eq(CL_ORD_ID), any(AdminOrderReplayCommand.class), eq(OPERATOR_ID), eq("corr-admin-replay")))
        .thenReturn(OrderReplayResult.of(
            CL_ORD_ID,
            "FAILED",
            null,
            null,
            null,
            null,
            null,
            null,
            "CONFIRMED",
            null,
            null,
            OPERATOR_ID,
            PROCESSED_AT
        ));

    service.replay(CL_ORD_ID, replayCommand(), actor());

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(channelScaffoldService).bootstrapTypedNotification(
        eq(session.getMemberId()),
        eq("ORDER"),
        eq("orderSessionId=" + session.getOrderSessionId()
            + " event=ORDER_FAILED status=FAILED executionResult= executionSource= executedQty= leavesQty="
            + " executedPrice= executedAt= failureReason=MANUAL_REJECT"),
        eq("ORDER_FAILED"),
        payloadCaptor.capture()
    );
    assertThat(payloadCaptor.getValue())
        .isEqualTo(Map.of(
            "type", "ORDER_FAILED",
            "orderSessionId", session.getOrderSessionId(),
            "clOrdId", CL_ORD_ID,
            "symbol", "005930",
            "side", "BUY",
            "failureReason", "MANUAL_REJECT",
            "timestamp", PROCESSED_AT
        ));
  }

  @Test
  void shouldPublishPartialFillCancelReplayNotification() {
    OrderSession session = escalatedSession();
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(java.util.Optional.of(session));
    when(corebankClient.replayOrder(eq(CL_ORD_ID), any(AdminOrderReplayCommand.class), eq(OPERATOR_ID), eq("corr-admin-replay")))
        .thenReturn(OrderReplayResult.of(
            CL_ORD_ID,
            "CANCELED",
            "PARTIAL_FILL_CANCEL",
            null,
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(72000),
            "FEP-510",
            "CONFIRMED",
            Instant.parse("2026-03-19T10:14:00Z"),
            Instant.parse("2026-03-19T10:16:00Z"),
            OPERATOR_ID,
            PROCESSED_AT
        ));

    service.replay(CL_ORD_ID, replayCommand(), actor());

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(channelScaffoldService).bootstrapTypedNotification(
        eq(session.getMemberId()),
        eq("ORDER"),
        eq("orderSessionId=" + session.getOrderSessionId()
            + " event=ORDER_PARTIAL_FILL_CANCEL status=CANCELED executionResult=PARTIAL_FILL_CANCEL"
            + " executionSource= executedQty=5 leavesQty=5 executedPrice=72000"
            + " executedAt=2026-03-19T10:14:00Z canceledAt=2026-03-19T10:16:00Z canceledQty=5"),
        eq("ORDER_PARTIAL_FILL_CANCEL"),
        payloadCaptor.capture()
    );
    assertThat(payloadCaptor.getValue())
        .isEqualTo(Map.of(
            "type", "ORDER_PARTIAL_FILL_CANCEL",
            "orderSessionId", session.getOrderSessionId(),
            "clOrdId", CL_ORD_ID,
            "symbol", "005930",
            "side", "BUY",
            "executedQty", BigDecimal.valueOf(5),
            "canceledQty", BigDecimal.valueOf(5),
            "executedPrice", BigDecimal.valueOf(72000),
            "executedAt", Instant.parse("2026-03-19T10:14:00Z"),
            "timestamp", PROCESSED_AT
        ));
  }

  @Test
  void shouldPublishCanceledReplayNotification() {
    OrderSession session = escalatedSession();
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(java.util.Optional.of(session));
    when(corebankClient.replayOrder(eq(CL_ORD_ID), any(AdminOrderReplayCommand.class), eq(OPERATOR_ID), eq("corr-admin-replay")))
        .thenReturn(OrderReplayResult.of(
            CL_ORD_ID,
            "CANCELED",
            "CANCELED",
            null,
            BigDecimal.ZERO,
            BigDecimal.TEN,
            null,
            "FEP-510",
            "CONFIRMED",
            null,
            Instant.parse("2026-03-19T10:16:00Z"),
            OPERATOR_ID,
            PROCESSED_AT
        ));

    service.replay(CL_ORD_ID, replayCommand(), actor());

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(channelScaffoldService).bootstrapTypedNotification(
        eq(session.getMemberId()),
        eq("ORDER"),
        eq("orderSessionId=" + session.getOrderSessionId()
            + " event=ORDER_CANCELED status=CANCELED executionResult=CANCELED executionSource="
            + " executedQty=0 leavesQty=10 executedPrice= executedAt= canceledAt=2026-03-19T10:16:00Z"),
        eq("ORDER_CANCELED"),
        payloadCaptor.capture()
    );
    assertThat(payloadCaptor.getValue())
        .isEqualTo(Map.of(
            "type", "ORDER_CANCELED",
            "orderSessionId", session.getOrderSessionId(),
            "clOrdId", CL_ORD_ID,
            "symbol", "005930",
            "side", "BUY",
            "canceledQty", BigDecimal.TEN,
            "canceledAt", Instant.parse("2026-03-19T10:16:00Z"),
            "timestamp", PROCESSED_AT
        ));
  }

  @Test
  void shouldEscapeReplayAuditValuesThatContainDelimiters() {
    OrderSession session = marketEscalatedSession();
    when(orderSessionRepository.findByClOrdIdForUpdate(CL_ORD_ID)).thenReturn(java.util.Optional.of(session));
    when(corebankClient.replayOrder(eq(CL_ORD_ID), any(AdminOrderReplayCommand.class), eq(OPERATOR_ID), eq("corr-admin-replay")))
        .thenReturn(OrderReplayResult.of(
            CL_ORD_ID,
            "COMPLETED",
            "VIRTUAL_FILL",
            "VIRTUAL_FILL",
            BigDecimal.TEN,
            BigDecimal.ZERO,
            BigDecimal.valueOf(72000),
            null,
            "CONFIRMED",
            Instant.parse("2026-03-19T10:14:00Z"),
            null,
            OPERATOR_ID,
            PROCESSED_AT
        ));

    service.replay(CL_ORD_ID, AdminOrderReplayCommand.of(
        "APPROVE",
        APPROVED_BY,
        "OPS,INC=20260319",
        "KRX outage, exchange=confirmed",
        72000L
    ), actor());

    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).record(auditLogCaptor.capture());
    assertThat(auditLogCaptor.getValue().getDetail()).contains("evidenceRef=OPS\\,INC\\=20260319");
    assertThat(auditLogCaptor.getValue().getDetail()).contains("reason=KRX outage\\, exchange\\=confirmed");
  }

  private OrderSession escalatedSession() {
    return escalatedSession("LIMIT", BigDecimal.valueOf(72000));
  }

  private OrderSession marketEscalatedSession() {
    return escalatedSession("MARKET", null);
  }

  private OrderSession escalatedSession(String orderType, BigDecimal price) {
    OrderSession session = OrderSession.initiated(
        301L,
        1L,
        CL_ORD_ID,
        ManualReplayIdentitySupport.replayFingerprint(CL_ORD_ID, "BUY", "BUY", "LIMIT", "qty", null, "owner"),
        "005930",
        "BUY",
        orderType,
        BigDecimal.TEN,
        price,
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.parse("2026-03-19T11:00:00Z")
    );
    session.startExecuting();
    session.escalate(OrderSession.ESCALATED_MANUAL_REVIEW);
    return session;
  }

  private AdminOrderReplayCommand replayCommand() {
    return AdminOrderReplayCommand.of(
        "APPROVE",
        APPROVED_BY,
        "OPS-INC-20260319-1",
        longReason(),
        72000L
    );
  }

  private AdminActorContext actor() {
    return AdminActorContext.of(
        900L,
        OPERATOR_ID,
        "ops-admin@fixyz.com",
        "admin-session-1",
        "127.0.0.1",
        "JUnit",
        "corr-admin-replay"
    );
  }

  private String longReason() {
    return "KRX outage resolved after manual exchange confirmation";
  }
}
