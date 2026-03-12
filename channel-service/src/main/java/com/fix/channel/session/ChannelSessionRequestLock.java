package com.fix.channel.session;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.locks.LockSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChannelSessionRequestLock {

  private static final int STRIPE_COUNT = 256;
  private static final Duration REDIS_LOCK_TTL = Duration.ofSeconds(30);
  private static final Duration REDIS_LOCK_WAIT_TIMEOUT = Duration.ofSeconds(5);
  private static final long REDIS_LOCK_RETRY_NANOS = Duration.ofMillis(25).toNanos();
  private static final String LOCK_KEY_PREFIX = "ch:session-order-create-lock:";
  private static final DefaultRedisScript<Long> ACQUIRE_LOCK_SCRIPT = createAcquireLockScript();
  private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = createReleaseLockScript();

  private final Object[] locks = new Object[STRIPE_COUNT];
  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  public ChannelSessionRequestLock(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.redisTemplateProvider = redisTemplateProvider;
    Arrays.setAll(locks, index -> new Object());
  }

  public <T> T executeLocked(String sessionId, Supplier<T> action) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      return executeRedisLocked(redisTemplate, sessionId, action);
    }

    Object lock = locks[Math.floorMod(sessionId.hashCode(), locks.length)];
    synchronized (lock) {
      return action.get();
    }
  }

  private <T> T executeRedisLocked(StringRedisTemplate redisTemplate, String sessionId, Supplier<T> action) {
    String lockKey = lockKey(sessionId);
    String lockToken = UUID.randomUUID().toString();
    if (!acquireWithRetry(redisTemplate, lockKey, lockToken)) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "channel session lock unavailable");
    }

    try {
      return action.get();
    } finally {
      releaseQuietly(redisTemplate, lockKey, lockToken);
    }
  }

  private boolean acquireWithRetry(StringRedisTemplate redisTemplate, String lockKey, String lockToken) {
    long deadline = System.nanoTime() + REDIS_LOCK_WAIT_TIMEOUT.toNanos();
    while (System.nanoTime() <= deadline) {
      Long acquired = executeAcquire(redisTemplate, lockKey, lockToken, REDIS_LOCK_TTL);
      if (Long.valueOf(1L).equals(acquired)) {
        return true;
      }
      LockSupport.parkNanos(REDIS_LOCK_RETRY_NANOS);
    }
    return false;
  }

  private void releaseQuietly(StringRedisTemplate redisTemplate, String lockKey, String lockToken) {
    try {
      executeRelease(redisTemplate, lockKey, lockToken);
    } catch (RuntimeException ex) {
      log.warn("failed to release channel session request lock for {}", lockKey, ex);
    }
  }

  protected Long executeAcquire(
      StringRedisTemplate redisTemplate,
      String lockKey,
      String lockToken,
      Duration ttl
  ) {
    return redisTemplate.execute(
        ACQUIRE_LOCK_SCRIPT,
        List.of(lockKey),
        lockToken,
        String.valueOf(ttl.toMillis())
    );
  }

  protected Long executeRelease(StringRedisTemplate redisTemplate, String lockKey, String lockToken) {
    return redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockToken);
  }

  private String lockKey(String sessionId) {
    return LOCK_KEY_PREFIX + sessionId;
  }

  private static DefaultRedisScript<Long> createAcquireLockScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText("""
        if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then
          return 1
        end
        return 0
        """);
    script.setResultType(Long.class);
    return script;
  }

  private static DefaultRedisScript<Long> createReleaseLockScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText("""
        if redis.call('GET', KEYS[1]) == ARGV[1] then
          return redis.call('DEL', KEYS[1])
        end
        return 0
        """);
    script.setResultType(Long.class);
    return script;
  }
}
