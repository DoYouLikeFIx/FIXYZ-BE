package com.fix.channel.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderSessionRecoveryAttemptStore {

  private static final String ATTEMPT_KEY_PREFIX = "ch:recovery-attempt:";
  private static final Duration ATTEMPT_TTL = Duration.ofHours(6);

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final Clock clock;
  private final ConcurrentMap<String, LocalAttempt> localAttempts = new ConcurrentHashMap<>();

  public OrderSessionRecoveryAttemptStore(
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      Clock clock
  ) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.clock = clock;
  }

  public int nextAttempt(String orderSessionId) {
    String key = ATTEMPT_KEY_PREFIX + orderSessionId;
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      Long nextValue = redisTemplate.opsForValue().increment(key);
      if (nextValue != null && nextValue == 1L) {
        redisTemplate.expire(key, ATTEMPT_TTL);
      }
      if (nextValue == null) {
        return 1;
      }
      if (nextValue > Integer.MAX_VALUE) {
        return Integer.MAX_VALUE;
      }
      return nextValue.intValue();
    }

    Instant now = Instant.now(clock);
    localAttempts.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    return localAttempts.compute(key, (unused, current) -> {
      if (current == null || current.expiresAt().isBefore(now)) {
        return new LocalAttempt(1, now.plus(ATTEMPT_TTL));
      }
      int nextCount = current.count() == Integer.MAX_VALUE ? Integer.MAX_VALUE : current.count() + 1;
      return new LocalAttempt(nextCount, current.expiresAt());
    }).count();
  }

  public void clear(String orderSessionId) {
    String key = ATTEMPT_KEY_PREFIX + orderSessionId;
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      redisTemplate.delete(key);
    }
    localAttempts.remove(key);
  }

  private record LocalAttempt(int count, Instant expiresAt) {
  }
}
