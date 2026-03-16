package com.fix.corebank.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {

  @Test
  void shouldDebitCashBalance() {
    Account account = Account.of(
        "110123456789",
        301L,
        "KRW",
        new BigDecimal("1000000.0000"),
        new BigDecimal("500.0000")
    );

    account.debitCash(new BigDecimal("140200.00000000"));

    assertThat(account.getCashBalance()).isEqualByComparingTo("859800.0000");
  }

  @Test
  void shouldCreditCashBalance() {
    Account account = Account.of(
        "110123456789",
        301L,
        "KRW",
        new BigDecimal("1000000.0000"),
        new BigDecimal("500.0000")
    );

    account.creditCash(new BigDecimal("140200.00000000"));

    assertThat(account.getCashBalance()).isEqualByComparingTo("1140200.0000");
  }

  @Test
  void shouldRejectCashDebitWhenBalanceWouldGoNegative() {
    Account account = Account.of(
        "110123456789",
        301L,
        "KRW",
        new BigDecimal("1000.0000"),
        new BigDecimal("500.0000")
    );

    assertThatThrownBy(() -> account.debitCash(new BigDecimal("1000.0001")))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.ORD_INSUFFICIENT_CASH));
  }

  @Test
  void shouldRejectNonPositiveCashMutations() {
    Account account = Account.of(
        "110123456789",
        301L,
        "KRW",
        new BigDecimal("1000.0000"),
        new BigDecimal("500.0000")
    );

    assertThatThrownBy(() -> account.creditCash(BigDecimal.ZERO))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.ORD_INVALID_REQUEST));
  }
}
