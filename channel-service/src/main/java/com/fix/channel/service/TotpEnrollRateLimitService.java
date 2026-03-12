package com.fix.channel.service;

import com.fix.common.error.ErrorCode;
import com.fix.common.error.RetryAfterBusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TotpEnrollRateLimitService {

  private static final String KEY_PREFIX = "ch:ratelimit:totp-enroll:";

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final Clock clock;
  private final ConcurrentMap<String, LocalCounter> localCounters = new ConcurrentHashMap<>();

  @Value("${auth.guardrails.totp-enroll.max-attempts:5}")
  private int maxAttempts;

  @Value("${auth.guardrails.totp-enroll.window-seconds:300}")
  private long windowSeconds;

  public TotpEnrollRateLimitService(
      ObjectProvider<StringRedisTemplate> redisTemplateProvider,
      Clock clock
  ) {
    this.redisTemplateProvider = redisTemplateProvider;
    this.clock = clock;
  }

  public void checkAllowed(String loginToken) {
    long currentFailures = currentAttempts(loginToken);
    if (currentFailures >= maxAttempts) {
      throw new RetryAfterBusinessException(
          ErrorCode.RATE_LIMIT_EXCEEDED,
          "rate limit exceeded",
          retryAfterSeconds(loginToken)
      );
    }
  }

  public void recordAttempt(String loginToken) {
    String normalizedToken = normalize(loginToken);
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      Long nextAttempts = redisTemplate.opsForValue().increment(key(normalizedToken));
      if (nextAttempts != null && nextAttempts == 1L) {
        redisTemplate.expire(key(normalizedToken), Duration.ofSeconds(Math.max(1L, windowSeconds)));
      }
      return;
    }

    localCounters.compute(normalizedToken, (ignored, counter) -> {
      Instant now = Instant.now(clock);
      if (counter == null || counter.expiresAt().isBefore(now)) {
        return new LocalCounter(1, now.plusSeconds(Math.max(1L, windowSeconds)));
      }
      return new LocalCounter(counter.attempts() + 1, counter.expiresAt());
    });
  }

  private long currentAttempts(String loginToken) {
    String normalizedToken = normalize(loginToken);
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      String raw = redisTemplate.opsForValue().get(key(normalizedToken));
      if (raw == null || raw.isBlank()) {
        return 0L;
      }
      try {
        return Long.parseLong(raw);
      } catch (NumberFormatException ignored) {
        return 0L;
      }
    }

    LocalCounter counter = localCounters.get(normalizedToken);
    if (counter == null) {
      return 0L;
    }
    if (counter.expiresAt().isBefore(Instant.now(clock))) {
      localCounters.remove(normalizedToken);
      return 0L;
    }
    return counter.attempts();
  }

  private long retryAfterSeconds(String loginToken) {
    String normalizedToken = normalize(loginToken);
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate != null) {
      Long ttlSeconds = redisTemplate.getExpire(key(normalizedToken));
      return ttlSeconds == null || ttlSeconds < 1L ? Math.max(1L, windowSeconds) : ttlSeconds;
    }

    LocalCounter counter = localCounters.get(normalizedToken);
    if (counter == null) {
      return Math.max(1L, windowSeconds);
    }
    return Math.max(1L, Duration.between(Instant.now(clock), counter.expiresAt()).getSeconds());
  }

  private String normalize(String loginToken) {
    if (loginToken == null || loginToken.isBlank()) {
      return "unknown";
    }
    return loginToken.trim();
  }

  private String key(String loginToken) {
    return KEY_PREFIX + loginToken;
  }

  private record LocalCounter(long attempts, Instant expiresAt) {
  }
}
