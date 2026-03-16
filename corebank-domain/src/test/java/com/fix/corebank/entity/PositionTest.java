package com.fix.corebank.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PositionTest {

  @Test
  void shouldApplyBuyAndRecalculateAveragePrice() {
    Position position = Position.of(
        1L,
        "005930",
        new BigDecimal("10.0000"),
        new BigDecimal("70000.0000")
    );

    position.applyBuy(new BigDecimal("5.0000"), new BigDecimal("73000.0000"));

    assertThat(position.getQty()).isEqualByComparingTo("15.0000");
    assertThat(position.getAvgPrice()).isEqualByComparingTo("71000.0000");
  }

  @Test
  void shouldApplyPartialSellWithoutChangingAveragePrice() {
    Position position = Position.of(
        1L,
        "005930",
        new BigDecimal("10.0000"),
        new BigDecimal("70000.0000")
    );

    position.applySell(new BigDecimal("4.0000"));

    assertThat(position.getQty()).isEqualByComparingTo("6.0000");
    assertThat(position.getAvgPrice()).isEqualByComparingTo("70000.0000");
  }

  @Test
  void shouldZeroAveragePriceWhenPositionIsFullySold() {
    Position position = Position.of(
        1L,
        "005930",
        new BigDecimal("10.0000"),
        new BigDecimal("70000.0000")
    );

    position.applySell(new BigDecimal("10.0000"));

    assertThat(position.getQty()).isEqualByComparingTo("0.0000");
    assertThat(position.getAvgPrice()).isEqualByComparingTo("0.0000");
  }

  @Test
  void shouldRejectSellWhenQuantityIsInsufficient() {
    Position position = Position.of(
        1L,
        "005930",
        new BigDecimal("2.0000"),
        new BigDecimal("70000.0000")
    );

    assertThatThrownBy(() -> position.applySell(new BigDecimal("3.0000")))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.ORD_INSUFFICIENT_POSITION));
  }

  @Test
  void shouldRejectNonPositivePositionMutations() {
    Position position = Position.of(
        1L,
        "005930",
        new BigDecimal("2.0000"),
        new BigDecimal("70000.0000")
    );

    assertThatThrownBy(() -> position.applyBuy(BigDecimal.ZERO, new BigDecimal("70000.0000")))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.ORD_INVALID_REQUEST));
  }
}
