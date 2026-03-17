package com.fix.channel.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderSessionStateMachineTest {

  @Test
  void shouldCreatePendingNewOrderSessionAndExpireIt() {
    Instant expiresAt = Instant.parse("2026-03-12T00:10:00Z");
    OrderSession session = OrderSession.initiated(
        1L,
        101L,
        "123e4567-e89b-42d3-a456-426614174260",
        "fingerprint",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        true,
        "ELEVATED_ORDER_RISK",
        expiresAt
    );

    assertNotNull(session.getOrderSessionId());
    assertEquals(OrderSessionStatus.PENDING_NEW, session.getStatus());
    assertEquals(101L, session.getAccountId());
    assertEquals("005930", session.getSymbol());
    assertEquals("BUY", session.getSide());
    assertEquals("LIMIT", session.getOrderType());
    assertEquals(expiresAt, session.getExpiresAt());

    session.expire();

    assertEquals(OrderSessionStatus.EXPIRED, session.getStatus());
  }

  @Test
  void shouldCreateLowRiskSessionDirectlyInAuthedState() {
    OrderSession session = OrderSession.initiated(
        1L,
        101L,
        "123e4567-e89b-42d3-a456-426614174261",
        "fingerprint",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(10000),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.parse("2026-03-12T00:10:00Z")
    );

    assertEquals(OrderSessionStatus.AUTHED, session.getStatus());
  }

  @Test
  void shouldRejectOutOfOrderTransitionFromPendingNewToExecuting() {
    OrderSession session = OrderSession.initiated(
        1L,
        101L,
        "123e4567-e89b-42d3-a456-426614174262",
        "fingerprint",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        true,
        "ELEVATED_ORDER_RISK",
        Instant.parse("2026-03-12T00:10:00Z")
    );

    BusinessException exception = assertThrows(BusinessException.class, session::startExecuting);

    assertEquals(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED, exception.getErrorCode());
    assertEquals(OrderSessionStatus.PENDING_NEW, session.getStatus());
  }

  @Test
  void shouldReservePendingNewToAuthedForChallengeRequiredSessionsOnly() {
    OrderSession lowRiskSession = OrderSession.initiated(
        1L,
        101L,
        "123e4567-e89b-42d3-a456-426614174263",
        "fingerprint",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.ONE,
        BigDecimal.valueOf(10000),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.parse("2026-03-12T00:10:00Z")
    );

    BusinessException exception = assertThrows(BusinessException.class, lowRiskSession::authorize);

    assertEquals(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED, exception.getErrorCode());
    assertEquals(OrderSessionStatus.AUTHED, lowRiskSession.getStatus());
  }

  @Test
  void shouldRejectTerminalStateReopenWithoutRecoveryPath() {
    OrderSession session = OrderSession.initiated(
        1L,
        101L,
        "123e4567-e89b-42d3-a456-426614174264",
        "fingerprint",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        true,
        "ELEVATED_ORDER_RISK",
        Instant.parse("2026-03-12T00:10:00Z")
    );

    session.authorize();
    session.startExecuting();
    session.complete(
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "FEP-0001",
        "CONFIRMED",
        Instant.parse("2026-03-12T00:02:00Z")
    );

    BusinessException exception = assertThrows(BusinessException.class, session::authorize);

    assertEquals(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED, exception.getErrorCode());
    assertEquals(OrderSessionStatus.COMPLETED, session.getStatus());
  }

  @Test
  void shouldEscalateExecutingSessionAndPreserveExecutionOutcomeWhenSnapshotExists() {
    OrderSession session = OrderSession.initiated(
        1L,
        101L,
        "123e4567-e89b-42d3-a456-426614174265",
        "fingerprint",
        "005930",
        "BUY",
        "LIMIT",
        BigDecimal.TEN,
        BigDecimal.valueOf(72000),
        false,
        "TRUSTED_AUTH_SESSION",
        Instant.parse("2026-03-12T00:10:00Z")
    );

    session.startExecuting();
    session.escalate(
        OrderSession.ESCALATED_MANUAL_REVIEW,
        "FILLED",
        BigDecimal.TEN,
        BigDecimal.ZERO,
        BigDecimal.valueOf(72000),
        "FEP-0002",
        "FAILED",
        Instant.parse("2026-03-12T00:06:30Z")
    );

    assertEquals(OrderSessionStatus.ESCALATED, session.getStatus());
    assertEquals(OrderSession.ESCALATED_MANUAL_REVIEW, session.getFailureReason());
    assertEquals("FILLED", session.getExecutionResult());
    assertEquals(BigDecimal.TEN, session.getExecutedQty());
    assertEquals(BigDecimal.ZERO, session.getLeavesQty());
    assertEquals(BigDecimal.valueOf(72000), session.getExecutedPrice());
    assertEquals("FEP-0002", session.getExternalOrderId());
    assertEquals("FAILED", session.getExternalSyncStatus());
    assertEquals(Instant.parse("2026-03-12T00:06:30Z"), session.getExecutedAt());
  }
}
