package com.fix.channel.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class OrderSessionRecoveryLockService {

  private static final Duration LOCK_TTL = Duration.ofSeconds(120);
  private static final String LOCK_KEY_PREFIX = "ch:recovery-lock:";
  private static final DefaultRedisScript<Long> RELEASE_IF_MATCHES_SCRIPT = createReleaseIfMatchesScript();

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final Clock clock;
  private final ConcurrentMap<String, LocalLock> localLocks = new ConcurrentHashMap<>();

  public OrderSessionRecoveryLockService(
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      Clock clock
  ) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.clock = clock;
  }

  public String tryAcquire(String orderSessionId) {
    String lockKey = lockKey(orderSessionId);
    String lockToken = UUID.randomUUID().toString();
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      try {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)) {
          return lockToken;
        }
        if (Boolean.FALSE.equals(acquired)) {
          return null;
        }
      } catch (RuntimeException ignored) {
        // Fall back to the in-process lock when Redis is temporarily unavailable.
      }
    }
    return tryAcquireLocally(lockKey, lockToken);
  }

  public void release(String orderSessionId, String lockToken) {
    if (lockToken == null || lockToken.isBlank()) {
      return;
    }
    String lockKey = lockKey(orderSessionId);
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      try {
        redisTemplate.execute(RELEASE_IF_MATCHES_SCRIPT, List.of(lockKey), lockToken);
      } catch (RuntimeException ignored) {
        // Local release below still cleans up fallback-acquired locks.
      }
    }
    localLocks.computeIfPresent(lockKey, (unused, current) -> current.token().equals(lockToken) ? null : current);
  }

  private String tryAcquireLocally(String lockKey, String lockToken) {
    Instant now = Instant.now(clock);
    LocalLock candidateLock = new LocalLock(lockToken, now.plus(LOCK_TTL));
    LocalLock acquired = localLocks.compute(lockKey, (unused, existing) -> {
      if (existing == null || !existing.expiresAt().isAfter(now)) {
        return candidateLock;
      }
      return existing;
    });
    return acquired == candidateLock ? lockToken : null;
  }

  private String lockKey(String orderSessionId) {
    return LOCK_KEY_PREFIX + orderSessionId;
  }

  private static DefaultRedisScript<Long> createReleaseIfMatchesScript() {
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

  private record LocalLock(String token, Instant expiresAt) {
  }
}
