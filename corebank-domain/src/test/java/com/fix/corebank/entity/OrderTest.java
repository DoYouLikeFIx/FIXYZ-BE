package com.fix.corebank.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderTest {

  @Test
  void shouldInitializeAcceptedOrderAsNew() {
    Order order = Order.accepted(
        1L,
        "123e4567-e89b-42d3-a456-426614174261",
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );

    assertThat(order.getStatus()).isEqualTo("NEW");
    assertThat(order.getExecutedQty()).isEqualByComparingTo("0.0000");
    assertThat(order.getLeavesQty()).isEqualByComparingTo("2.0000");
  }

  @Test
  void shouldCompleteExecutionAndClearFailureReason() {
    Order order = Order.accepted(
        1L,
        "123e4567-e89b-42d3-a456-426614174260",
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );
    order.updateState("UNKNOWN", Order.EXTERNAL_SYNC_FAILED, "FEP-001", "TIMEOUT");

    order.completeExecution(
        "FILLED",
        "FILLED",
        new BigDecimal("2.0000"),
        BigDecimal.ZERO,
        new BigDecimal("70100.0000"),
        Instant.parse("2026-03-16T00:00:00Z")
    );

    assertThat(order.getStatus()).isEqualTo("FILLED");
    assertThat(order.getFailureReason()).isNull();
    assertThat(order.getExecutionResult()).isEqualTo("FILLED");
    assertThat(order.getExecutedQty()).isEqualByComparingTo("2.0000");
    assertThat(order.getLeavesQty()).isEqualByComparingTo("0.0000");
    assertThat(order.getExecutedPrice()).isEqualByComparingTo("70100.0000");
    assertThat(order.getExecutedAt()).isEqualTo(Instant.parse("2026-03-16T00:00:00Z"));
  }

  @Test
  void shouldRejectNegativeExecutionSummaryValues() {
    Order order = Order.accepted(
        1L,
        "123e4567-e89b-42d3-a456-426614174260",
        "005930",
        "BUY",
        new BigDecimal("2.0000"),
        new BigDecimal("70100.0000")
    );

    assertThatThrownBy(() -> order.completeExecution(
        "FILLED",
        "FILLED",
        new BigDecimal("-1.0000"),
        BigDecimal.ZERO,
        new BigDecimal("70100.0000"),
        Instant.parse("2026-03-16T00:00:00Z")
    ))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.ORD_INVALID_REQUEST));
  }
}
