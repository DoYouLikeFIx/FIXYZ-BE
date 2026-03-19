package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class OrderSessionRecoveryLockServiceTest {

  private MutableClock clock;
  private OrderSessionRecoveryLockService orderSessionRecoveryLockService;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-03-19T00:00:00Z"));
    orderSessionRecoveryLockService = new OrderSessionRecoveryLockService(
        new StaticListableBeanFactory().getBeanProvider(org.springframework.data.redis.core.StringRedisTemplate.class),
        clock
    );
  }

  @Test
  void shouldCleanupExpiredFallbackLocksDuringLaterOperations() throws Exception {
    for (int i = 0; i < 3; i++) {
      assertThat(orderSessionRecoveryLockService.tryAcquire("stale-" + i)).isNotBlank();
    }
    assertThat(localLocks()).hasSize(3);

    clock.advance(Duration.ofSeconds(121));

    for (int i = 0; i < 50; i++) {
      String token = orderSessionRecoveryLockService.tryAcquire("active-" + i);
      assertThat(token).isNotBlank();
      orderSessionRecoveryLockService.release("active-" + i, token);
    }

    assertThat(localLocks()).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private Map<String, ?> localLocks() throws Exception {
    Field field = OrderSessionRecoveryLockService.class.getDeclaredField("localLocks");
    field.setAccessible(true);
    return (Map<String, ?>) field.get(orderSessionRecoveryLockService);
  }

  private static final class MutableClock extends Clock {

    private Instant currentInstant;

    private MutableClock(Instant currentInstant) {
      this.currentInstant = currentInstant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return currentInstant;
    }

    private void advance(Duration duration) {
      currentInstant = currentInstant.plus(duration);
    }
  }
}
