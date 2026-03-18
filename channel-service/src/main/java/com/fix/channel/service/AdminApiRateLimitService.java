package com.fix.channel.service;

import com.fix.common.error.BusinessException;
import com.fix.common.error.ErrorCode;
import com.fix.common.error.RetryAfterBusinessException;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class AdminApiRateLimitService {

  private static final String KEY_PREFIX = "ch:ratelimit:admin:session:";
  private static final DefaultRedisScript<Long> INCREMENT_WITH_WINDOW_SCRIPT = createIncrementWithWindowScript();

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  @Value("${channel.admin.rate-limit.max-attempts:20}")
  private int maxAttempts;

  @Value("${channel.admin.rate-limit.window-seconds:60}")
  private long windowSeconds;

  public AdminApiRateLimitService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.redisTemplateProvider = redisTemplateProvider;
  }

  public void enforce(String sessionId) {
    String key = KEY_PREFIX + sanitizeSessionId(sessionId);
    StringRedisTemplate redisTemplate = requireRateLimitRedis();
    Long current = redisTemplate.execute(
        INCREMENT_WITH_WINDOW_SCRIPT,
        List.of(key),
        String.valueOf(Duration.ofSeconds(Math.max(1L, windowSeconds)).toMillis())
    );
    if (current == null) {
      throw rateLimitUnavailable();
    }
    if (current <= maxAttempts) {
      return;
    }
    throw new RetryAfterBusinessException(
        ErrorCode.RATE_LIMIT_EXCEEDED,
        "rate limit exceeded",
        retryAfterSeconds(redisTemplate, key)
    );
  }

  private String sanitizeSessionId(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return "unknown";
    }
    return sessionId.trim();
  }

  private long retryAfterSeconds(StringRedisTemplate redisTemplate, String key) {
    Long rawTtl = redisTemplate.getExpire(key);
    if (rawTtl == null) {
      return Math.max(1L, windowSeconds);
    }
    if (rawTtl > 0L) {
      return rawTtl;
    }
    if (rawTtl == -1L) {
      return Math.max(1L, windowSeconds);
    }
    return 1L;
  }

  private StringRedisTemplate requireRateLimitRedis() {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      throw rateLimitUnavailable();
    }
    return redisTemplate;
  }

  private BusinessException rateLimitUnavailable() {
    return new BusinessException(ErrorCode.INTERNAL_ERROR, "admin api rate limit unavailable");
  }

  private static DefaultRedisScript<Long> createIncrementWithWindowScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText("""
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
          redis.call('PEXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """);
    script.setResultType(Long.class);
    return script;
  }
}
