package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class OrderSessionExecutionLockServiceTest {

  private OrderSessionExecutionLockService orderSessionExecutionLockService;

  @BeforeEach
  void setUp() {
    orderSessionExecutionLockService = new OrderSessionExecutionLockService(
        new StaticListableBeanFactory().getBeanProvider(org.springframework.data.redis.core.StringRedisTemplate.class),
        Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneOffset.UTC)
    );
  }

  @Test
  void shouldAllowImmediateReacquireAfterRelease() {
    orderSessionExecutionLockService.acquire("session-001");

    orderSessionExecutionLockService.release("session-001");

    assertThatCode(() -> orderSessionExecutionLockService.acquire("session-001"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectAcquireWhenLockAlreadyHeld() {
    orderSessionExecutionLockService.acquire("session-001");

    assertThatThrownBy(() -> orderSessionExecutionLockService.acquire("session-001"))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.ORDER_SESSION_EXECUTION_IN_PROGRESS));
  }
}
