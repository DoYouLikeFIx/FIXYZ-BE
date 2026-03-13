package com.fix.channel.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderSessionExecutionLockService {

  private static final Duration LOCK_TTL = Duration.ofSeconds(30);
  private static final String LOCK_KEY_PREFIX = "ch:txn-lock:";

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final Clock clock;
  private final ConcurrentMap<String, Instant> localLocks = new ConcurrentHashMap<>();

  public OrderSessionExecutionLockService(
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      Clock clock
  ) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.clock = clock;
  }

  public void acquire(String orderSessionId) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey(orderSessionId), "1", LOCK_TTL);
      if (Boolean.FALSE.equals(acquired)) {
        throw alreadyExecuting();
      }
      return;
    }

    Instant now = Instant.now(clock);
    localLocks.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    Instant previous = localLocks.putIfAbsent(lockKey(orderSessionId), now.plus(LOCK_TTL));
    if (previous != null && previous.isAfter(now)) {
      throw alreadyExecuting();
    }
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

  private BusinessException alreadyExecuting() {
    return new BusinessException(
        ErrorCode.ORDER_SESSION_EXECUTION_IN_PROGRESS,
        "order execution already in progress"
    );
  }
}
