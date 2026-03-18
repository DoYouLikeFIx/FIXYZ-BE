package com.fix.channel.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    assertThat(session.getOrderSessionId()).isNotNull();
    assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.PENDING_NEW);
    assertThat(session.getAccountId()).isEqualTo(101L);
    assertThat(session.getSymbol()).isEqualTo("005930");
    assertThat(session.getSide()).isEqualTo("BUY");
    assertThat(session.getOrderType()).isEqualTo("LIMIT");
    assertThat(session.getExpiresAt()).isEqualTo(expiresAt);

    session.expire();

    assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.EXPIRED);
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

    assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.AUTHED);
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

    assertThatThrownBy(session::startExecuting)
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED));
    assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.PENDING_NEW);
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

    assertThatThrownBy(lowRiskSession::authorize)
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED));
    assertThat(lowRiskSession.getStatus()).isEqualTo(OrderSessionStatus.AUTHED);
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

    assertThatThrownBy(session::authorize)
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.ORDER_SESSION_NOT_AUTHORIZED));
    assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.COMPLETED);
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

    assertThat(session.getStatus()).isEqualTo(OrderSessionStatus.ESCALATED);
    assertThat(session.getFailureReason()).isEqualTo(OrderSession.ESCALATED_MANUAL_REVIEW);
    assertThat(session.getExecutionResult()).isEqualTo("FILLED");
    assertThat(session.getExecutedQty()).isEqualTo(BigDecimal.TEN);
    assertThat(session.getLeavesQty()).isEqualTo(BigDecimal.ZERO);
    assertThat(session.getExecutedPrice()).isEqualTo(BigDecimal.valueOf(72000));
    assertThat(session.getExternalOrderId()).isEqualTo("FEP-0002");
    assertThat(session.getExternalSyncStatus()).isEqualTo("FAILED");
    assertThat(session.getExecutedAt()).isEqualTo(Instant.parse("2026-03-12T00:06:30Z"));
  }
}
