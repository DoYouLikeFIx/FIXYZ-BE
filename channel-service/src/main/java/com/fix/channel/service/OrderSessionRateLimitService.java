package com.fix.channel.service;

import com.fix.common.error.ErrorCode;
import com.fix.common.error.BusinessException;
import com.fix.common.error.RetryAfterBusinessException;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderSessionRateLimitService {

  private static final String KEY_PREFIX = "ch:ratelimit:order-prepare:";
  private static final DefaultRedisScript<Long> INCREMENT_WITH_WINDOW_SCRIPT = createIncrementWithWindowScript();
  private static final DefaultRedisScript<Long> DECREMENT_IF_PRESENT_SCRIPT = createDecrementIfPresentScript();

  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

  @Value("${order.session.rate-limit.enabled:true}")
  private boolean enabled;

  @Value("${order.session.rate-limit.max-attempts:10}")
  private int maxAttempts;

  @Value("${order.session.rate-limit.window-seconds:60}")
  private long windowSeconds;

  public void enforceCreateRateLimit(Long memberId) {
    if (!enabled) {
      return;
    }

    StringRedisTemplate redisTemplate = requireRateLimitRedis();
    String key = KEY_PREFIX + sanitizeMemberId(memberId);
    Long current = executeIncrement(redisTemplate, key);
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

  public void refundCreateRateLimit(Long memberId) {
    if (!enabled) {
      return;
    }

    Long refunded = executeRefund(requireRateLimitRedis(), KEY_PREFIX + sanitizeMemberId(memberId));
    if (refunded == null) {
      throw rateLimitUnavailable();
    }
  }

  private long retryAfterSeconds(StringRedisTemplate redisTemplate, String key) {
    Long rawTtl = redisTemplate.getExpire(key);
    if (rawTtl == null || rawTtl < 1L) {
      return Math.max(1L, windowSeconds);
    }
    return rawTtl;
  }

  private String sanitizeMemberId(Long memberId) {
    return memberId == null ? "unknown" : String.valueOf(memberId);
  }

  protected Long executeIncrement(StringRedisTemplate redisTemplate, String key) {
    return redisTemplate.execute(
        INCREMENT_WITH_WINDOW_SCRIPT,
        List.of(key),
        String.valueOf(Duration.ofSeconds(Math.max(1L, windowSeconds)).toMillis())
    );
  }

  protected Long executeRefund(StringRedisTemplate redisTemplate, String key) {
    return redisTemplate.execute(DECREMENT_IF_PRESENT_SCRIPT, List.of(key));
  }

  private StringRedisTemplate requireRateLimitRedis() {
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      throw rateLimitUnavailable();
    }
    return redisTemplate;
  }

  private BusinessException rateLimitUnavailable() {
    return new BusinessException(ErrorCode.INTERNAL_ERROR, "order session rate limit unavailable");
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

  private static DefaultRedisScript<Long> createDecrementIfPresentScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptText("""
        local current = redis.call('GET', KEYS[1])
        if not current then
          return 0
        end
        current = tonumber(current)
        if current <= 1 then
          redis.call('DEL', KEYS[1])
          return 0
        end
        return redis.call('DECR', KEYS[1])
        """);
    script.setResultType(Long.class);
    return script;
  }
}
