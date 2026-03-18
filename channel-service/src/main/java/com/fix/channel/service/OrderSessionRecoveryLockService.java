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
public class OrderSessionRecoveryLockService {

  private static final Duration LOCK_TTL = Duration.ofSeconds(120);
  private static final String LOCK_KEY_PREFIX = "ch:recovery-lock:";

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final Clock clock;
  private final ConcurrentMap<String, Instant> localLocks = new ConcurrentHashMap<>();

  public OrderSessionRecoveryLockService(
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      Clock clock
  ) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.clock = clock;
  }

  public boolean tryAcquire(String orderSessionId) {
    String lockKey = lockKey(orderSessionId);
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
      return !Boolean.FALSE.equals(acquired);
    }

    Instant now = Instant.now(clock);
    localLocks.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    Instant previous = localLocks.putIfAbsent(lockKey, now.plus(LOCK_TTL));
    return previous == null || !previous.isAfter(now);
  }

  public void release(String orderSessionId) {
    String lockKey = lockKey(orderSessionId);
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      redisTemplate.delete(lockKey);
    }
    localLocks.remove(lockKey);
  }

  private String lockKey(String orderSessionId) {
    return LOCK_KEY_PREFIX + orderSessionId;
  }
}
