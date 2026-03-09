package com.fix.channel.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginIpRateLimitService {

  private static final String KEY_PREFIX = "ch:ratelimit:login:";

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  @Value("${auth.guardrails.ip-rate-limit.max-failed-attempts:5}")
  private int maxFailedAttempts;

  @Value("${auth.guardrails.ip-rate-limit.window-seconds:60}")
  private long windowSeconds;

  public boolean isBlocked(String ipAddress) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return false;
    }

    Long currentFailures = readFailures(redisTemplate, key(ipAddress));
    return currentFailures >= maxFailedAttempts;
  }

  public long recordFailure(String ipAddress) {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return 0;
    }

    String key = key(ipAddress);
    Long nextFailures = redisTemplate.opsForValue().increment(key);
    if (nextFailures == null) {
      return 0;
    }

    if (nextFailures == 1L) {
      redisTemplate.expire(key, Duration.ofSeconds(Math.max(1L, windowSeconds)));
    }

    return nextFailures;
  }

  private Long readFailures(StringRedisTemplate redisTemplate, String key) {
    String raw = redisTemplate.opsForValue().get(key);
    if (raw == null || raw.isBlank()) {
      return 0L;
    }
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException ignored) {
      return 0L;
    }
  }

  private String key(String ipAddress) {
    if (ipAddress == null || ipAddress.isBlank()) {
      return KEY_PREFIX + "unknown";
    }
    return KEY_PREFIX + ipAddress.trim();
  }
}
