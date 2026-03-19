package com.fix.channel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

class OrderSessionRecoveryAttemptStoreTest {

  @Test
  void shouldKeepLocalFallbackAttemptExpiryFixedAfterFirstIncrement() throws Exception {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
    when(redisProvider.getIfAvailable()).thenReturn(null);
    MutableClock clock = new MutableClock(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC);
    OrderSessionRecoveryAttemptStore store = new OrderSessionRecoveryAttemptStore(redisProvider, clock);

    assertThat(store.nextAttempt("session-1")).isEqualTo(1);
    Instant firstExpiry = localAttemptExpiry(store, "session-1");

    clock.advance(Duration.ofHours(1));

    assertThat(store.nextAttempt("session-1")).isEqualTo(2);
    Instant secondExpiry = localAttemptExpiry(store, "session-1");

    assertThat(secondExpiry).isEqualTo(firstExpiry);
  }

  private Instant localAttemptExpiry(OrderSessionRecoveryAttemptStore store, String orderSessionId) throws Exception {
    Field attemptsField = OrderSessionRecoveryAttemptStore.class.getDeclaredField("localAttempts");
    attemptsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, ?> attempts = (Map<String, ?>) attemptsField.get(store);
    Object attempt = attempts.get("ch:recovery-attempt:" + orderSessionId);
    assertThat(attempt).isNotNull();

    Method expiresAtMethod = attempt.getClass().getDeclaredMethod("expiresAt");
    expiresAtMethod.setAccessible(true);
    return (Instant) expiresAtMethod.invoke(attempt);
  }

  private static final class MutableClock extends Clock {

    private Instant current;
    private final ZoneId zoneId;

    private MutableClock(Instant current, ZoneId zoneId) {
      this.current = current;
      this.zoneId = zoneId;
    }

    private void advance(Duration duration) {
      current = current.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new MutableClock(current, zone);
    }

    @Override
    public Instant instant() {
      return current;
    }
  }
}
